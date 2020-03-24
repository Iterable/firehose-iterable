package com.mparticle.iterable;

public abstract class UserRequest {
    /**
     * Either email or userId must be passed in to identify the user.  If both are passed in, email takes precedence.
     */
    public String email;

    /**
     * Optional userId, typically your database generated id. Either email or userId must be specified.
     */
    public String userId;
}
