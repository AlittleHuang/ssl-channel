// package org.example.network.jray;
//
// import org.example.network.pipe.PipeContext;
// import org.example.network.pipe.handlers.AuthHandlers;
//
// import java.io.IOException;
// import java.nio.ByteBuffer;
//
// public class KeeperServerHandler extends ConnectionKeeper {
//
//     @Override
//     public void init(PipeContext ctx) {
//         ctx.addBefore(AuthHandlers.server());
//     }
//
//     @Override
//     public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
//         try {
//             doOnReceive(ctx, buf);
//         } finally {
//             if (!buf.hasRemaining()) {
//                 ctx.free(buf);
//             }
//         }
//     }
//
//     private static void doOnReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
//         while (buf.hasRemaining()) {
//             byte b = buf.get();
//             if (b == KEEP) {
//                 ctx.fireWrite(ByteBuffer.wrap(new byte[]{KEEP}));
//             } else if (b == REMOVE) {
//                 if (buf.hasRemaining()) {
//                     ctx.fireReceive(buf);
//                 }
//                 ctx.remove();
//                 return;
//             } else {
//                 ctx.fireClose();
//             }
//         }
//     }
//
// }
