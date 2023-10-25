package org.example.network.event;

import org.example.network.pipe.Pipeline;

public interface PipelineReadableListener {

    void isReadable(Pipeline pipeline);

}
