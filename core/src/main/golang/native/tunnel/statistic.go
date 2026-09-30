package tunnel

import (
	"github.com/metacubex/mihomo/tunnel/statistic"
)

func ResetStatistic() {
	statistic.DefaultManager.ResetStatistic()
}

func Now() (up int64, down int64) {
	return statistic.DefaultManager.Now()
}

func Total() (up int64, down int64) {
	return statistic.DefaultManager.Total()
}

func ActiveConnections() int {
	count := 0
	statistic.DefaultManager.Range(func(statistic.Tracker) bool {
		count++
		return true
	})
	return count
}

func Memory() uint64 {
	return statistic.DefaultManager.Memory()
}
