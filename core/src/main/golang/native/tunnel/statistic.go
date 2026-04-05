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

// QueryConnectionsSnapshot returns live trackers + totals as JSON (see tunnel/statistic Snapshot).
func QueryConnectionsSnapshot() *statistic.Snapshot {
	return statistic.DefaultManager.Snapshot()
}
