package octo.core.flags

/** Optional features owned by the core; every one is off unless `config.json` turns it on. */
object CoreFlags {
    val GIT_PUBLISH = FeatureFlag("git-publish", "Commit, push and open a pull request from a task")
}
