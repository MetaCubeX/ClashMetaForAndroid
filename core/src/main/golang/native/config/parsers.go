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
		log.Debugln("[ApplyParsers] 读取配置文件失败: %v", err)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	originalContent := string(data)
	parsedContent := applyParsersWithUrl(ctx, originalContent, parsersUrl)
	if parsedContent == originalContent {
		return
	}

	log.Debugln("[ApplyParsers] 最终subscribe:%s", parsedContent)

	if err := os.WriteFile(configPath, []byte(parsedContent), 0600); err != nil {
		log.Debugln("[ApplyParsers] 写入parsers后配置失败: %v", err)
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
		log.Debugln("[ApplyParsers] failed to parse YAML: %v", err)
		return subscribeOriginalStr
	}

	log.Debugln("[ApplyParsers] 找到parsersURL: %s", parsersUrl)
	parsersContainerYml, parsersErr := openUrlAsYaml(ctx, parsersUrl)
	if parsersErr != nil {
		log.Debugln("[ApplyParsers] 拉取parsers失败: %v", parsersErr)
		return subscribeOriginalStr
	}

	parsersContainer, parsersContainerExist := parsersContainerYml["parsers"].(map[string]interface{})
	if !parsersContainerExist {
		log.Debugln("[ApplyParsers] parsers容器中不存在parsers节点")
		return subscribeOriginalStr
	}

	parsers, parsersExist := parsersContainer["yaml"].(map[string]interface{})
	if !parsersExist {
		log.Debugln("[ApplyParsers] parsers容器中不存在yaml节点")
		return subscribeOriginalStr
	}

	subscribe = modifyArr(subscribe, "proxies", parsers, "prepend-proxies", true)
	subscribe = modifyArr(subscribe, "proxies", parsers, "append-proxies", false)
	subscribe = modifyArr(subscribe, "proxy-groups", parsers, "prepend-proxy-groups", true)
	subscribe = modifyArr(subscribe, "proxy-groups", parsers, "append-proxy-groups", false)
	subscribe = modifyArr(subscribe, "rules", parsers, "prepend-rules", true)
	subscribe = modifyArr(subscribe, "rules", parsers, "append-rules", false)
	subscribe = modifyGroupProxies(subscribe, parsers, "prepend-to-group", true)
	subscribe = modifyGroupProxies(subscribe, parsers, "append-to-group", false)

	yamlBytes, err := yaml.Marshal(subscribe)
	if err != nil {
		log.Debugln("[ApplyParsers] failed to marshal YAML: %v", err)
		return subscribeOriginalStr
	}

	return string(yamlBytes)
}

func modifyArr(subscribe map[string]interface{}, subscribeKey string, parsers map[string]interface{}, parserKey string, isPrepend bool) map[string]interface{} {
	if arrToModify, arrToModifyExist := parsers[parserKey].([]interface{}); arrToModifyExist {
		log.Debugln("[ApplyParsers] parsers找到%s", parserKey)
		if originalArr, originalArrExist := subscribe[subscribeKey].([]interface{}); originalArrExist {
			log.Debugln("[ApplyParsers] subscribe找到%s", subscribeKey)
			log.Debugln("[ApplyParsers] subscribe原始%s:%v", subscribeKey, originalArr)
			if isPrepend {
				originalArr = append(arrToModify, originalArr...)
			} else {
				originalArr = append(originalArr, arrToModify...)
			}
			subscribe[subscribeKey] = originalArr
			log.Debugln("[ApplyParsers] subscribe编辑后%s:%v", subscribeKey, subscribe[subscribeKey])
		} else {
			subscribe[subscribeKey] = arrToModify
			log.Debugln("[ApplyParsers] subscribe编辑后%s:%v", subscribeKey, subscribe[subscribeKey])
		}
	} else {
		log.Debugln("[ApplyParsers] parsers未找到%s", parserKey)
	}
	return subscribe
}

func modifyGroupProxies(subscribe map[string]interface{}, parsers map[string]interface{}, parserKey string, isPrepend bool) map[string]interface{} {
	rules, rulesExist := parsers[parserKey].([]interface{})
	if !rulesExist {
		log.Debugln("[ApplyParsers] parsers未找到%s", parserKey)
		return subscribe
	}
	log.Debugln("[ApplyParsers] parsers找到%s", parserKey)

	proxyGroups, groupsExist := subscribe["proxy-groups"].([]interface{})
	if !groupsExist {
		log.Debugln("[ApplyParsers] subscribe未找到proxy-groups")
		return subscribe
	}

	for _, ruleItem := range rules {
		ruleMap, ok := ruleItem.(map[string]interface{})
		if !ok {
			continue
		}
		targetGroupName, nameOk := ruleMap["group"].(string)
		proxiesToAdd, proxiesOk := ruleMap["proxies"].([]interface{})
		if !nameOk || !proxiesOk || len(proxiesToAdd) == 0 {
			continue
		}

		for i, groupItem := range proxyGroups {
			groupMap, ok := groupItem.(map[string]interface{})
			if !ok {
				continue
			}
			groupName, ok := groupMap["name"].(string)
			if !ok || groupName != targetGroupName {
				continue
			}

			if originalProxies, exists := groupMap["proxies"].([]interface{}); exists {
				if isPrepend {
					groupMap["proxies"] = append(proxiesToAdd, originalProxies...)
				} else {
					groupMap["proxies"] = append(originalProxies, proxiesToAdd...)
				}
			} else {
				copied := make([]interface{}, len(proxiesToAdd))
				copy(copied, proxiesToAdd)
				groupMap["proxies"] = copied
			}
			proxyGroups[i] = groupMap
			log.Debugln("[ApplyParsers] subscribe编辑后proxy-group[%s]:%v", targetGroupName, groupMap["proxies"])
		}
	}

	subscribe["proxy-groups"] = proxyGroups
	return subscribe
}
