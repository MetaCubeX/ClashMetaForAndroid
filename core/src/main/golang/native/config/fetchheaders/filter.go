package fetchheaders

import "strings"

var allowlist = map[string]struct{}{
	"support-url": {}, "x-support-url": {}, "support_url": {},
	"profile-support-url": {}, "subscription-support-url": {}, "support": {},
	"profile-title": {}, "subscription-title": {}, "x-subscription-title": {},
	"display-name": {}, "x-display-name": {}, "subscription-display-name": {},
	"content-disposition":  {},
	"profile-web-page-url": {}, "subscription-web-page": {}, "x-subscription-web-page": {},
	"profile-update-interval": {}, "update-interval": {}, "x-profile-update-interval": {},
	"announce": {}, "announcement": {}, "x-announce": {}, "x-announcement": {},
	"announce-url": {}, "announcement-url": {}, "x-announce-url": {}, "x-announcement-url": {},
	"subscription-userinfo": {},
	"share-links":           {}, "share_links": {}, "x-share-links": {}, "x-share-links-policy": {},
	"x-hwid-active": {}, "x-hwid-not-supported": {}, "x-hwid-max-devices-reached": {}, "x-hwid-limit": {},
	"x-branding-enabled": {},
	"x-brand-name":       {}, "x-brand-tagline": {}, "x-brand-logo-url": {}, "x-brand-logo-light-url": {},
	"x-brand-accent-color": {}, "x-brand-website-url": {}, "x-brand-support-url": {},
	"x-brand-telegram-url": {}, "x-brand-bot-url": {}, "x-brand-privacy-url": {},
	"x-brand-terms-url": {}, "x-brand-help-url": {}, "x-brand-status-url": {},
	"x-brand-renew-url": {}, "x-brand-cabinet-url": {}, "x-brand-user-display-name": {},
	"x-brand-greeting": {}, "x-brand-hide-routing": {}, "x-brand-hide-global-mode": {},
	"x-brand-show-operator-tab": {},
}

func Filter(header map[string][]string) map[string]string {
	flat := make(map[string]string)
	for key, values := range header {
		lowerKey := strings.ToLower(key)
		if _, allowed := allowlist[lowerKey]; !allowed || len(values) == 0 {
			continue
		}
		flat[lowerKey] = strings.Join(values, ", ")
	}
	return flat
}
