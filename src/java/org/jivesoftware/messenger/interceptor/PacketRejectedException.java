/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.interceptor;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Thrown by a PacketInterceptor when a packet is prevented from being processed. If the packet was
 * received then it will not be processed and a not_allowed error will be sent back to the sender
 * of the packet. If the packet was going to be sent then the sending will be aborted.
 *
 * @see PacketInterceptor
 * @author Gaston Dombiak
 */
public class PacketRejectedException extends Exception {
    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    public PacketRejectedException() {
        super();
    }

    public PacketRejectedException(String msg) {
        super(msg);
    }

    public PacketRejectedException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public PacketRejectedException(String msg, Throwable nestedThrowable) {
        super(msg);
        this.nestedThrowable = nestedThrowable;
    }

    public void printStackTrace() {
        super.printStackTrace();
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace();
        }
    }

    public void printStackTrace(PrintStream ps) {
        super.printStackTrace(ps);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(ps);
        }
    }

    public void printStackTrace(PrintWriter pw) {
        super.printStackTrace(pw);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(pw);
        }
    }
}
