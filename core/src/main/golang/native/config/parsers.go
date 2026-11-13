package config

import (
	"context"
	"io"
	"net/http"
	U "net/url"
	"os"
	P "path"
	"strings"
	"time"

	"cfa/native/app"

	clashHttp "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/log"
	"gopkg.in/yaml.v3"
)

const parserUrlFileName = "parser.url"

func resolveParsersUrl(profileDir string, subscribeUrl *U.URL) string {
	if data, err := os.ReadFile(P.Join(profileDir, parserUrlFileName)); err == nil {
		if u := strings.TrimSpace(string(data)); u != "" {
			return u
		}
	}

	if subscribeUrl != nil && subscribeUrl.Query().Has("parsers") {
		if u := strings.TrimSpace(subscribeUrl.Query().Get("parsers")); u != "" {
			return u
		}
	}

	return ""
}

func applyParsersToConfig(profileDir string, configPath string, subscribeUrl *U.URL) {
	parsersUrl := resolveParsersUrl(profileDir, subscribeUrl)
	if parsersUrl == "" {
		return
	}

	data, err := os.ReadFile(configPath)
	if err != nil {
		log.Debugln("读取配置文件失败: %v", err)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	originalContent := string(data)
	parsedContent := applyParsersWithUrl(ctx, originalContent, parsersUrl)
	if parsedContent == originalContent {
		return
	}

	log.Debugln("最终subscribe:%s", parsedContent)

	if err := os.WriteFile(configPath, []byte(parsedContent), 0600); err != nil {
		log.Debugln("写入parsers后配置失败: %v", err)
	}
}

func openUrlNoHeader(ctx context.Context, url string) (io.ReadCloser, error) {
	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet, http.Header{"User-Agent": {"ClashMetaForAndroid/" + app.VersionName()}}, nil)
	if err != nil {
		return nil, err
	}

	return response.Body, nil
}

func openUrlAsString(ctx context.Context, url string) (string, error) {
	body, requestErr := openUrlNoHeader(ctx, url)
	if requestErr != nil {
		return "", requestErr
	}
	defer body.Close()

	data, err := io.ReadAll(body)
	if err != nil {
		return "", err
	}

	return string(data), nil
}

func openUrlAsYaml(ctx context.Context, url string) (map[string]interface{}, error) {
	content, err := openUrlAsString(ctx, url)
	if err != nil {
		return nil, err
	}

	var cfg map[string]interface{}
	if err := yaml.Unmarshal([]byte(content), &cfg); err != nil {
		return nil, err
	}

	return cfg, nil
}

func applyParsersWithUrl(ctx context.Context, subscribeOriginalStr string, parsersUrl string) string {
	var subscribe map[string]interface{}
	if err := yaml.Unmarshal([]byte(subscribeOriginalStr), &subscribe); err != nil {
		log.Debugln("failed to parse YAML: %v", err)
		return subscribeOriginalStr
	}

	log.Debugln("找到parsersURL: %s", parsersUrl)
	parsersContainerYml, parsersErr := openUrlAsYaml(ctx, parsersUrl)
	if parsersErr != nil {
		log.Debugln("拉取parsers失败: %v", parsersErr)
		return subscribeOriginalStr
	}

	parsersContainer, parsersContainerExist := parsersContainerYml["parsers"].(map[string]interface{})
	if !parsersContainerExist {
		log.Debugln("parsers容器中不存在parsers节点")
		return subscribeOriginalStr
	}

	parsers, parsersExist := parsersContainer["yaml"].(map[string]interface{})
	if !parsersExist {
		log.Debugln("parsers容器中不存在yaml节点")
		return subscribeOriginalStr
	}

	subscribe = prependArr(subscribe, "proxies", parsers, "prepend-proxies")
	subscribe = prependArr(subscribe, "proxy-groups", parsers, "prepend-proxy-groups")
	subscribe = prependArr(subscribe, "rules", parsers, "prepend-rules")

	yamlBytes, err := yaml.Marshal(subscribe)
	if err != nil {
		log.Debugln("failed to marshal YAML: %v", err)
		return subscribeOriginalStr
	}

	return string(yamlBytes)
}

func prependArr(subscribe map[string]interface{}, subscribeKey string, parsers map[string]interface{}, parserKey string) map[string]interface{} {
	if arrToPrepend, arrToPrependExist := parsers[parserKey].([]interface{}); arrToPrependExist {
		log.Debugln("parses找到%s", parserKey)
		if originalArr, originalArrExist := subscribe[subscribeKey].([]interface{}); originalArrExist {
			log.Debugln("subscribe找到%s", subscribeKey)
			log.Debugln("subscribe原始%s:%v", subscribeKey, originalArr)
			originalArr = append(arrToPrepend, originalArr...)
			subscribe[subscribeKey] = originalArr
			log.Debugln("subscribe编辑后%s:%v", subscribeKey, subscribe[subscribeKey])
		} else {
			subscribe[subscribeKey] = arrToPrepend
			log.Debugln("subscribe编辑后%s:%v", subscribeKey, subscribe[subscribeKey])
		}
	} else {
		log.Debugln("parses未找到%s", parserKey)
	}
	return subscribe
}
