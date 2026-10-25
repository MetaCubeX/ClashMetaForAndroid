package config

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	U "net/url"
	"os"
	P "path"
	"runtime"
	"strconv"
	"strings"
	"time"

	"cfa/native/app"

	"github.com/metacubex/mihomo/adapter/provider"
	clashHttp "github.com/metacubex/mihomo/component/http"
	RB "github.com/metacubex/mihomo/rules/bundle"
)

type Status struct {
	Action            string   `json:"action"`
	Args              []string `json:"args"`
	Progress          int      `json:"progress"`
	MaxProgress       int      `json:"max"`
	SubUpload         *int64   `json:"subUpload,omitempty"`
	SubDownload       *int64   `json:"subDownload,omitempty"`
	SubTotal          *int64   `json:"subTotal,omitempty"`
	SubExpire         *int64   `json:"subExpire,omitempty"`
	SubUpdateInterval *int64   `json:"subUpdateInterval,omitempty"`
}

type fetchHeader struct {
	SubscriptionUserInfo  string
	ProfileUpdateInterval string
}

func openUrl(ctx context.Context, url string) (io.ReadCloser, fetchHeader, error) {
	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet, http.Header{"User-Agent": {"ClashMetaForAndroid/" + app.VersionName()}}, nil)

	if err != nil {
		return nil, fetchHeader{}, err
	}

	return response.Body, fetchHeader{
		SubscriptionUserInfo:  response.Header.Get("subscription-userinfo"),
		ProfileUpdateInterval: response.Header.Get("profile-update-interval"),
	}, nil
}

func openContent(url string) (io.ReadCloser, error) {
	return app.OpenContent(url)
}

func fetch(url *U.URL, file string) (fetchHeader, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	var reader io.ReadCloser
	var header fetchHeader
	var err error

	switch url.Scheme {
	case "http", "https":
		reader, header, err = openUrl(ctx, url.String())
	case "content":
		reader, err = openContent(url.String())
	default:
		err = fmt.Errorf("unsupported scheme %s of %s", url.Scheme, url)
	}

	if err != nil {
		return fetchHeader{}, err
	}

	defer reader.Close()

	return header, writeFile(file, reader)
}

func writeFile(file string, reader io.Reader) error {
	_ = os.MkdirAll(P.Dir(file), 0700)

	tmp, err := os.CreateTemp(P.Dir(file), ".cmfa-*")
	if err != nil {
		return err
	}

	tmpName := tmp.Name()
	_, copyErr := io.Copy(tmp, reader)
	closeErr := tmp.Close()
	if copyErr != nil {
		_ = os.Remove(tmpName)
		return copyErr
	}
	if closeErr != nil {
		_ = os.Remove(tmpName)
		return closeErr
	}

	if err = os.Rename(tmpName, file); err != nil {
		_ = os.Remove(tmpName)
		return err
	}

	return nil
}

func parseProfileUpdateInterval(value string) (int64, bool) {
	hours, err := strconv.ParseInt(strings.TrimSpace(value), 10, 64)
	if err != nil {
		return 0, false
	}

	if hours <= 0 {
		return 0, true
	}

	interval := time.Duration(hours) * time.Hour
	if interval < 15*time.Minute {
		interval = 15 * time.Minute
	}

	return int64(interval / time.Millisecond), true
}

func reportSubscriptionInfo(header fetchHeader, reportStatus func(string)) {
	userinfo := header.SubscriptionUserInfo
	updateIntervalHeader := header.ProfileUpdateInterval
	if userinfo == "" && updateIntervalHeader == "" {
		return
	}

	status := Status{
		Action:      "SubscriptionInfo",
		Args:        []string{},
		Progress:    -1,
		MaxProgress: -1,
	}

	if userinfo != "" {
		info := provider.NewSubscriptionInfo(userinfo)
		expire := info.Expire * 1000
		status.SubUpload = &info.Upload
		status.SubDownload = &info.Download
		status.SubTotal = &info.Total
		status.SubExpire = &expire
	}

	if interval, ok := parseProfileUpdateInterval(updateIntervalHeader); ok {
		status.SubUpdateInterval = &interval
	}

	bytes, _ := json.Marshal(&status)
	reportStatus(string(bytes))
}

func FetchAndValid(
	path string,
	url string,
	force bool,
	reportStatus func(string),
) error {
	defer runtime.GC()

	configPath := P.Join(path, "config.yaml")
	_, statErr := os.Stat(configPath)
	fetchConfig := force || os.IsNotExist(statErr)

	var configURL *U.URL
	if fetchConfig {
		parsed, err := U.Parse(url)
		if err != nil {
			return err
		}
		configURL = parsed
	}

	staging, err := profileStagingDir(path)
	if err != nil {
		return err
	}
	defer os.RemoveAll(staging)

	var header fetchHeader
	if fetchConfig {
		bytes, _ := json.Marshal(&Status{
			Action:      "FetchConfiguration",
			Args:        []string{configURL.Host},
			Progress:    -1,
			MaxProgress: -1,
		})

		reportStatus(string(bytes))

		header, err = fetch(configURL, P.Join(staging, "config.yaml"))
		if err != nil {
			return err
		}
	} else if err = copyFile(P.Join(staging, "config.yaml"), configPath); err != nil {
		return err
	}

	rawCfg, err := UnmarshalAndPatch(staging)
	if err != nil {
		return err
	}

	var staged []string
	err = forEachProviders(rawCfg, func(index int, total int, name string, provider map[string]any, prefix string) error {
		bytes, _ := json.Marshal(&Status{
			Action:      "FetchProviders",
			Args:        []string{name},
			Progress:    index,
			MaxProgress: total,
		})

		reportStatus(string(bytes))

		u, uok := provider["url"]
		p, pok := provider["path"]

		if !uok || !pok {
			return nil
		}

		us, uok := u.(string)
		ps, pok := p.(string)

		if !uok || !pok {
			return nil
		}

		rel, err := relativeToStaging(staging, ps)
		if err != nil {
			return err
		}

		if !force {
			officialPs := P.Join(path, rel)
			if _, err := os.Stat(officialPs); err == nil {
				return copyFile(ps, officialPs)
			} else if !os.IsNotExist(err) {
				return err
			}
		}

		url, err := U.Parse(us)
		if err != nil {
			return err
		}

		if prefix == RULES {
			if pib, uok := provider["path-in-bundle"]; uok {
				if pib, uok := pib.(string); uok && pib != "" {
					// actually, we don't need to extract the file here; the core will do it.
					// however, due to historical reasons, CMFA fetches provider content when loading profile,
					// so we maintain consistency with the old behavior.
					if file, err := RB.Open(pib); err == nil {
						defer file.Close()
						if err := writeFile(ps, file); err == nil {
							staged = append(staged, rel)
							return nil
						}
					}
				}
			}
		}

		if _, err := fetch(url, ps); err != nil {
			return err
		}

		staged = append(staged, rel)
		return nil
	})
	if err != nil {
		return err
	}

	bytes, _ := json.Marshal(&Status{
		Action:      "Verifying",
		Args:        []string{},
		Progress:    0xffff,
		MaxProgress: 0xffff,
	})

	reportStatus(string(bytes))

	cfg, err := Parse(rawCfg)
	if err != nil {
		return err
	}

	destroyProviders(cfg)

	rels := staged
	if fetchConfig {
		rels = append([]string{"config.yaml"}, staged...)
	}
	if err := publishStaged(path, staging, rels); err != nil {
		return err
	}

	if fetchConfig {
		reportSubscriptionInfo(header, reportStatus)
	}

	return nil
}

// profileStagingDir keeps the fetch scratch directory beside the profile.
// os.MkdirTemp with an empty dir uses os.TempDir, and on Android that is
// /data/local/tmp, which the app cannot create.
func profileStagingDir(profilePath string) (string, error) {
	dir := P.Dir(profilePath)
	if dir == "" || dir == "." || dir == profilePath {
		return "", fmt.Errorf("profile path %s has no parent directory", profilePath)
	}
	if err := os.MkdirAll(dir, 0700); err != nil {
		return "", err
	}
	return os.MkdirTemp(dir, ".cmfa-profile-*")
}

func relativeToStaging(stagingDir, stagedPath string) (string, error) {
	prefix := stagingDir + "/"
	if !strings.HasPrefix(stagedPath, prefix) {
		return "", fmt.Errorf("staged file %s is outside %s", stagedPath, stagingDir)
	}

	return strings.TrimPrefix(stagedPath, prefix), nil
}

func copyFile(dst, src string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	return writeFile(dst, in)
}

func copyToTemp(dir, src string) (string, error) {
	in, err := os.Open(src)
	if err != nil {
		return "", err
	}
	defer in.Close()

	tmp, err := os.CreateTemp(dir, ".cmfa-bak-*")
	if err != nil {
		return "", err
	}

	name := tmp.Name()
	_, copyErr := io.Copy(tmp, in)
	closeErr := tmp.Close()
	if copyErr != nil {
		_ = os.Remove(name)
		return "", copyErr
	}
	if closeErr != nil {
		_ = os.Remove(name)
		return "", closeErr
	}

	return name, nil
}

func missingParents(dir string) ([]string, error) {
	var missing []string
	cur := dir
	for {
		_, err := os.Stat(cur)
		if err == nil {
			break
		}
		if !os.IsNotExist(err) {
			return nil, err
		}

		missing = append(missing, cur)
		parent := P.Dir(cur)
		if parent == cur {
			break
		}
		cur = parent
	}

	return missing, nil
}

func validPublishRel(rel string) bool {
	if rel == "" || strings.HasPrefix(rel, "/") {
		return false
	}

	for _, part := range strings.Split(rel, "/") {
		if part == "" || part == "." || part == ".." {
			return false
		}
	}

	return true
}

type stagedPublish struct {
	dst     string
	backup  string
	created bool
	dirs    []string
}

func publishStaged(officialDir, stagingDir string, rels []string) error {
	var done []stagedPublish
	rollback := func() {
		for i := len(done) - 1; i >= 0; i-- {
			item := done[i]
			switch {
			case item.created:
				_ = os.Remove(item.dst)
			case item.backup != "":
				_ = copyFile(item.dst, item.backup)
			}
			for _, dir := range item.dirs {
				_ = os.Remove(dir)
			}
		}
	}

	for _, rel := range rels {
		if !validPublishRel(rel) {
			rollback()
			return fmt.Errorf("refuse to publish %s", rel)
		}

		dst := P.Join(officialDir, rel)
		parent := P.Dir(dst)
		dirs, err := missingParents(parent)
		if err != nil {
			rollback()
			return err
		}
		if err = os.MkdirAll(parent, 0700); err != nil {
			rollback()
			return err
		}

		item := stagedPublish{dirs: dirs}
		info, statErr := os.Stat(dst)
		switch {
		case statErr == nil && info.IsDir():
			done = append(done, item)
			rollback()
			return fmt.Errorf("%s is a directory", dst)
		case statErr == nil:
			backup, berr := copyToTemp(stagingDir, dst)
			if berr != nil {
				done = append(done, item)
				rollback()
				return berr
			}
			item.backup = backup
			item.dst = dst
		case os.IsNotExist(statErr):
			item.created = true
			item.dst = dst
		default:
			done = append(done, item)
			rollback()
			return statErr
		}

		if err = copyFile(dst, P.Join(stagingDir, rel)); err != nil {
			if item.backup != "" {
				_ = os.Remove(item.backup)
			}
			item.dst = ""
			item.backup = ""
			item.created = false
			done = append(done, item)
			rollback()
			return err
		}

		done = append(done, item)
	}

	for _, item := range done {
		if item.backup != "" {
			_ = os.Remove(item.backup)
		}
	}

	return nil
}
