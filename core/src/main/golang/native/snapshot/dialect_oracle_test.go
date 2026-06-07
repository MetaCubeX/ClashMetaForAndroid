package snapshot

import (
	"testing"

	"gopkg.in/yaml.v3"
)

// Baseline: documents how the engine's YAML (gopkg.in/yaml.v3, YAML 1.2 core)
// resolves the scalars our Kotlin SnakeYAML pipeline gets wrong. Our WRITE side
// must match THIS.
func TestEngineYaml_DialectBaseline(t *testing.T) {
	var m map[string]any
	src := "" +
		"a: off\n" +
		"b: yes\n" +
		"g: on\n" +
		"h: no\n" +
		"c: 0755\n" +
		"d: 1_000\n" +
		"i: 22:00\n" +
		"e: true\n" +
		"f: 7890\n"
	if err := yaml.Unmarshal([]byte(src), &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	str := func(k string) {
		if s, ok := m[k].(string); !ok {
			t.Errorf("%s => %#v, want string", k, m[k])
		} else {
			t.Logf("%s => string %q", k, s)
		}
	}
	// THE divergence: 1.1 bool barewords are STRINGS for the engine (1.2 core).
	str("a") // off
	str("b") // yes
	str("g") // on
	str("h") // no
	// NOT a divergence: yaml.v3 ALSO octal/underscore-coerces these to int,
	// exactly like SnakeYAML — so our re-dump (493/1000) is value-identical.
	if _, ok := m["c"].(int); !ok {
		t.Errorf("0755 => %#v, want int (engine coerces octal too)", m["c"])
	}
	if _, ok := m["d"].(int); !ok {
		t.Errorf("1_000 => %#v, want int (engine coerces underscores too)", m["d"])
	}
	t.Logf("22:00 => %#v", m["i"]) // characterize sexagesimal
	if b, ok := m["e"].(bool); !ok || !b {
		t.Errorf("true => %#v, want bool true", m["e"])
	}
	if _, ok := m["f"].(int); !ok {
		t.Errorf("7890 => %#v, want int", m["f"])
	}
}
