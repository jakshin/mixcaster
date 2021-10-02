/*
 * Copyright (C) 2016 Jason Jackson
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jakshin.mixcaster.http;

import java.net.*;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * A minimal HTTP server which listens for incoming HTTP connections,
 * then hands them off to an HttpResponse for processing on a separate thread.
 * It maintains a thread pool to serve incoming connections more quickly.
 */
public class HttpServer implements Runnable {
    /**
     * Creates a new instance of the class.
     */
    public HttpServer() {
        String httpPortStr = System.getProperty("http_port");
        this.port = Integer.parseInt(httpPortStr);  // already validated

        // 3 threads min, 300 threads max, wait 30s before killing idle threads;
        // LinkedBlockingQueue is FIFO, so we process HTTP requests in the order they're received
        int min = 3, max = 300, keepAliveTime = 30;
        this.pool = new ThreadPoolExecutor(min, max, keepAliveTime, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    /**
     * Runs the HTTP server, listening for HTTP connections on the configured port,
     * and passing each connection off in a separate thread for processing.
     */
    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public void run() {
        ServerSocket serverSocket;

        try {
            // bind to the configured TCP port
            serverSocket = new ServerSocket(this.port);
            logger.log(INFO, () -> String.format("Listening for HTTP connections on port %d", this.port));
        }
        catch (IOException ex) {
            logger.log(ERROR, ex, () -> String.format("HTTP server failed to bind to TCP port %d", this.port));
            return;
        }

        while (true) {
            try {
                // listen for connections; this will block until a connection is received
                Socket socket = serverSocket.accept();  //NOPMD closed by HttpResponse

                // connection received, process it on a separate thread
                HttpResponse response = new HttpResponse(socket);
                this.pool.execute(response);
            }
            catch (IOException ex) {
                // a SocketException means the socket was closed
                logger.log(ERROR, ex, ex::getMessage);
            }
        }
    }

    /** The TCP port on which the HTTP server listens for connections. */
    private final int port;

    /** The pool of threads which process HTTP requests. */
    private final ThreadPoolExecutor pool;
}
