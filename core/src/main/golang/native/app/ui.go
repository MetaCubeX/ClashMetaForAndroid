package app

import (
	"regexp"

	"cfa/native/common"

	"github.com/metacubex/mihomo/log"
)

var uiSubtitlePattern *regexp.Regexp

func ApplySubtitlePattern(pattern string) {
	if pattern == "" {
		uiSubtitlePattern = nil

		return
	}

	if o := uiSubtitlePattern; o != nil && o.String() == pattern {
		return
	}

	reg, err := common.CompileSubtitlePattern(pattern)
	if err == nil {
		uiSubtitlePattern = reg
	} else {
		uiSubtitlePattern = nil

		log.Warnln("Compile ui-subtitle-pattern: %s", err.Error())
	}
}

func SubtitlePattern() *regexp.Regexp {
	return uiSubtitlePattern
}
