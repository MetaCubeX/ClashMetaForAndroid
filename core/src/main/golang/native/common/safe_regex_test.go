package common

import (
	"strings"
	"testing"
)

func TestCompileSubtitlePatternUsesLinearTimeEngine(t *testing.T) {
	re, err := CompileSubtitlePattern(`(a+)+$`)
	if err != nil {
		t.Fatalf("RE2-safe nested quantifier should compile: %v", err)
	}
	if re.MatchString(strings.Repeat("a", 100_000) + "!") {
		t.Fatal("non-matching suffix unexpectedly matched")
	}
}

func TestCompileSubtitlePatternRejectsUnsupportedOrOversizedPatterns(t *testing.T) {
	if _, err := CompileSubtitlePattern(`(.)\1`); err == nil {
		t.Fatal("RE2-incompatible backreference must be rejected during validation")
	}
	if _, err := CompileSubtitlePattern(strings.Repeat("a", maxSubtitlePatternBytes+1)); err == nil {
		t.Fatal("oversized pattern must be rejected")
	}
}
