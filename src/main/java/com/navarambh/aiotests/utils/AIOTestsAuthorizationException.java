package com.navarambh.aiotests.utils;

public class AIOTestsAuthorizationException extends AIOTestsException {

    AIOTestsAuthorizationException() {
        super("User is unauthorized.  Please check API key and if user has access to project.");
    }

}
