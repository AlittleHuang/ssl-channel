package org.example.network.tcp;

import org.example.network.buf.Bytes;
import org.example.network.pipe.PipeHandler;

public class PipeConfig {

    public PipeHandler handler;

    public int bufCapacity = Bytes.DEF_CAP;

    public boolean autoRead = true;

}
