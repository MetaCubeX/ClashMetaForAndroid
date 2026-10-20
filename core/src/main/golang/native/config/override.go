package config

import (
	"io"
	"os"
	"path"

	"github.com/metacubex/mihomo/constant"
)

type OverrideSlot int

const (
	OverrideSlotPersist OverrideSlot = iota
	OverrideSlotSession
)

const defaultPersistOverride = `{}`
const defaultSessionOverride = `{}`

var sessionOverride = defaultSessionOverride

func overridePersistPath() string {
	return constant.Path.Resolve("override.json")
}

func ReadOverride(slot OverrideSlot) (string, error) {
	switch slot {
	case OverrideSlotPersist:
		file, err := os.OpenFile(overridePersistPath(), os.O_RDONLY, 0600)
		if err != nil {
			return defaultPersistOverride, nil
		}
		defer file.Close()

		buf, err := io.ReadAll(file)
		if err != nil {
			return "", err
		}

		return string(buf), nil
	case OverrideSlotSession:
		return sessionOverride, nil
	}

	return "", nil
}

func WriteOverride(slot OverrideSlot, content string) error {
	switch slot {
	case OverrideSlotPersist:
		dest := overridePersistPath()
		tmp, err := os.CreateTemp(path.Dir(dest), "override-*.json")
		if err != nil {
			return err
		}

		tmpName := tmp.Name()
		payload := []byte(content)
		n, werr := tmp.Write(payload)
		cerr := tmp.Close()
		if werr != nil {
			_ = os.Remove(tmpName)
			return werr
		}
		if n != len(payload) {
			_ = os.Remove(tmpName)
			return io.ErrShortWrite
		}
		if cerr != nil {
			_ = os.Remove(tmpName)
			return cerr
		}
		if err = os.Rename(tmpName, dest); err != nil {
			_ = os.Remove(tmpName)
			return err
		}

		return nil
	case OverrideSlotSession:
		sessionOverride = content
		return nil
	}

	return nil
}

func ClearOverride(slot OverrideSlot) {
	switch slot {
	case OverrideSlotPersist:
		_ = os.Remove(overridePersistPath())
	case OverrideSlotSession:
		sessionOverride = defaultSessionOverride
	}
}
