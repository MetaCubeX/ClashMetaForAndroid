package config

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/metacubex/mihomo/log"
	"gopkg.in/yaml.v3"
	"io"
	"net/http"
	U "net/url"
	"os"
	P "path"
	"runtime"
	"time"

	"cfa/native/app"

	clashHttp "github.com/metacubex/mihomo/component/http"
)

type Status struct {
	Action      string   `json:"action"`
	Args        []string `json:"args"`
	Progress    int      `json:"progress"`
	MaxProgress int      `json:"max"`
}

func openUrl(ctx context.Context, url string) (io.ReadCloser, error) {
	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet, http.Header{"User-Agent": {"ClashMetaForAndroid/" + app.VersionName()}}, nil)

	if err != nil {
		return nil, err
	}

	return response.Body, nil
}

func openUrlAsString(ctx context.Context, url string) (string, error) {
	body, requestErr := openUrl(ctx, url)

	if requestErr != nil {
		return "", requestErr
	}

	// 读取所有数据并转换为byte数组
	data, err := io.ReadAll(body)
	defer body.Close()
	if err != nil {
		return "", err
	}
	// 将数据转为字符串
	content := string(data)
	return content, nil
}

func openUrlAsYaml(ctx context.Context, url string) (map[string]interface{}, error) {
	content, _ := openUrlAsString(ctx, url)
	// 定义一个结构体来存储 YAML 解析结果
	var config map[string]interface{} // 假设 config 是一个 map
	// 解析 YAML 内容
	err := yaml.Unmarshal([]byte(content), &config)
	if err != nil {
		return nil, err
	}
	return config, nil
}

func openContent(url string) (io.ReadCloser, error) {
	return app.OpenContent(url)
}

func fetch(url *U.URL, file string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	var reader io.ReadCloser
	var err error

	switch url.Scheme {
	case "http", "https":
		reader, err = openUrl(ctx, url.String())
	case "content":
		reader, err = openContent(url.String())
	default:
		err = fmt.Errorf("unsupported scheme %s of %s", url.Scheme, url)
	}

	if err != nil {
		return err
	}

	defer reader.Close()

	data, err := io.ReadAll(reader)
	if err != nil {
		return err
	}
	content := string(data)
	parsedContent := applyParsers(ctx, content, url)
	log.Debugln("最终subscribe:%s", parsedContent)

	_ = os.MkdirAll(P.Dir(file), 0700)

	f, err := os.OpenFile(file, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0600)
	if err != nil {
		return err
	}

	defer f.Close()

	_, err = f.WriteString(parsedContent)
	if err != nil {
		_ = os.Remove(file)
	}

	return err
}

func applyParsers(ctx context.Context, subscribeOriginalStr string, subscribeUrl *U.URL) string {
	if !subscribeUrl.Query().Has("parsers") {
		log.Debugln("需要处理parsers")
		return subscribeOriginalStr
	}

	// 定义一个结构体来存储 YAML 解析结果
	var subscribe map[string]interface{}

	// 解析 YAML 内容
	err := yaml.Unmarshal([]byte(subscribeOriginalStr), &subscribe)
	if err != nil {
		// 如果解析出错，返回错误信息作为字符串
		log.Debugln("failed to parse YAML: %v", err)
		return fmt.Sprintf("failed to parse YAML: %v", err)
	}

	var parsersUrl = subscribeUrl.Query().Get("parsers")
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

	// 将解析后的数据结构转回 YAML 格式的字符串
	yamlBytes, err := yaml.Marshal(subscribe)
	if err != nil {
		log.Debugln("failed to marshal YAML: %v", err)
		return fmt.Sprintf("failed to marshal YAML: %v", err)
	}

	// 返回解析后的 YAML 字符串
	return string(yamlBytes)
}

func prependArr(subscribe map[string]interface{}, subscribeKey string, parsers map[string]interface{}, parserKey string) map[string]interface{} {
	// 处理prepend-rules
	if arrToPrepend, arrToPrependExist := parsers[parserKey].([]interface{}); arrToPrependExist {
		log.Debugln("parses找到%s", parserKey)
		// 提取 originalArr 字段
		if originalArr, originalArrExist := subscribe[subscribeKey].([]interface{}); originalArrExist {
			log.Debugln("subscribe找到%s", subscribeKey)
			// 将新的规则添加到 originalArr 数组的头部
			log.Debugln("subscribe原始%s:%v", subscribeKey, originalArr)
			originalArr = append(arrToPrepend, originalArr...)
			// 更新 subscribe 中的 originalArr 字段
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

func FetchAndValid(
	path string,
	url string,
	force bool,
	reportStatus func(string),
) error {
	configPath := P.Join(path, "config.yaml")

	if _, err := os.Stat(configPath); os.IsNotExist(err) || force {
		url, err := U.Parse(url)
		if err != nil {
			return err
		}

		bytes, _ := json.Marshal(&Status{
			Action:      "FetchConfiguration",
			Args:        []string{url.Host},
			Progress:    -1,
			MaxProgress: -1,
		})

		reportStatus(string(bytes))

		if err := fetch(url, configPath); err != nil {
			return err
		}
	}

	defer runtime.GC()

	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		return err
	}

	forEachProviders(rawCfg, func(index int, total int, name string, provider map[string]any, prefix string) {
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
			return
		}

		us, uok := u.(string)
		ps, pok := p.(string)

		if !uok || !pok {
			return
		}

		if _, err := os.Stat(ps); err == nil {
			return
		}

		url, err := U.Parse(us)
		if err != nil {
			return
		}

		_ = fetch(url, ps)
	})

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

	return nil
}
