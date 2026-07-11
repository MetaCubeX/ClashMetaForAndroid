package common

import (
	"fmt"
	"regexp"
)

const maxSubtitlePatternBytes = 512

// CompileSubtitlePattern deliberately uses Go's RE2-backed regexp engine.
// Profile-controlled patterns are evaluated for every proxy name, so a
// backtracking engine would turn nested quantifiers into a connect-time DoS.
func CompileSubtitlePattern(pattern string) (*regexp.Regexp, error) {
	if len(pattern) > maxSubtitlePatternBytes {
		return nil, fmt.Errorf("pattern exceeds %d bytes", maxSubtitlePatternBytes)
	}
	return regexp.Compile("(?i:" + pattern + ")")
}
