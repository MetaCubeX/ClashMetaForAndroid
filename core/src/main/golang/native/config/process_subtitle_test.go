package config

import (
	"strings"
	"testing"
)

func TestSanitizeUiSubtitlePatternDoesNotRejectProfileForUiOnlyPattern(t *testing.T) {
	if got := sanitizeUiSubtitlePattern(`(.)\1`); got != "" {
		t.Fatalf("unsupported backtracking pattern must be ignored, got %q", got)
	}
	if got := sanitizeUiSubtitlePattern(strings.Repeat("a", 513)); got != "" {
		t.Fatalf("oversized pattern must be ignored, got %q", got)
	}
	if got := sanitizeUiSubtitlePattern(`US|DE`); got != `US|DE` {
		t.Fatalf("RE2-safe pattern changed: %q", got)
	}
}
