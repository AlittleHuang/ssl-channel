package org.example.network.jray;

import org.example.network.pipe.PipeHandler;

public abstract class ConnectionKeeper implements PipeHandler {

    public static final byte KEEP = 0;
    public static final byte REMOVE = 1;

}
