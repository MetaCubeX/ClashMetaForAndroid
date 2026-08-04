package com.github.kr328.clash.agent.authorization

import kotlinx.serialization.Serializable

/**
 * Controls when an agent operation must stop and ask the user for approval.
 *
 * This setting only applies to tools deliberately exposed by the app. Security
 * invariants such as secret redaction and the absence of arbitrary shell access
 * are enforced outside this policy and cannot be disabled by a model.
 */
@Serializable
enum class AgentAuthorizationMode {
    /** Every operation that changes state requires explicit approval. */
    CAUTIOUS,

    /** Routine changes run automatically; high-impact changes require approval. */
    BALANCED,

    /** Every exposed operation runs automatically, including critical changes. */
    FULL_AUTO,
}
