//go:build !(android && cmfa)

package app

func NotifyDnsChanged(string) {}
