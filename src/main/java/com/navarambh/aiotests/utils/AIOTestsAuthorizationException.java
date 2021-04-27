package com.navarambh.aiotests.utils;

public class AIOTestsAuthorizationException extends AIOTestsException {

    AIOTestsAuthorizationException() {
        super("User is unauthorized.  Please check API token of whether user has access to project.");
    }

}
