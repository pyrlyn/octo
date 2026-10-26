package octo.core.http

/** The token in an `Authorization: Bearer <token>` header, or null when there is none. */
fun bearerToken(authorization: String?): String? =
    authorization
        ?.removePrefix("Bearer ")
        ?.takeIf { it != authorization && it.isNotEmpty() }
