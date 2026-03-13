/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.izykube.web.ws;

import com.izylife.izykube.services.PodShellGateway;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class PodShellWebSocketHandler extends TextWebSocketHandler {

    private static final String SESSION_KEY = "podShellSession";

    private final PodShellGateway podShellGateway;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> params = extractQueryParams(session);
        String namespace = params.get("namespace");
        String pod = params.get("pod");
        String container = params.get("container");

        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(pod)) {
            sendErrorAndClose(session, "Namespace and pod are required.", CloseStatus.BAD_DATA);
            return;
        }

        try {
            WebSocketOutputStream outputStream = new WebSocketOutputStream(session);
            ExecWatch execWatch = podShellGateway.openShell(
                    namespace,
                    pod,
                    container,
                    outputStream,
                    outputStream,
                    new PodShellExecListener(session));
            OutputStream inputWriter = execWatch.getInput();
            session.getAttributes().put(SESSION_KEY, new ShellSession(execWatch, inputWriter, outputStream));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            sendErrorAndClose(session, ex.getMessage(), CloseStatus.POLICY_VIOLATION);
        } catch (Exception ex) {
            log.error("Failed to open pod shell session", ex);
            sendErrorAndClose(session, "Failed to open shell session.", CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ShellSession shellSession = (ShellSession) session.getAttributes().get(SESSION_KEY);
        if (shellSession == null) {
            return;
        }
        shellSession.writeToPod(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        closeShellSession(session);
        super.afterConnectionClosed(session, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Transport error on pod shell session: {}", exception.getMessage());
        closeShellSession(session);
        super.handleTransportError(session, exception);
    }

    private void closeShellSession(WebSocketSession session) {
        ShellSession shellSession = (ShellSession) session.getAttributes().remove(SESSION_KEY);
        if (shellSession != null) {
            shellSession.close();
        }
    }

    private Map<String, String> extractQueryParams(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return Map.of();
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
    }

    private void sendErrorAndClose(WebSocketSession session, String message, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
                session.close(new CloseStatus(status.getCode(), message));
            }
        } catch (IOException ex) {
            log.warn("Unable to close shell session cleanly: {}", ex.getMessage());
        }
    }

    private static class ShellSession {
        private final ExecWatch execWatch;
        private final OutputStream inputWriter;
        private final WebSocketOutputStream outputStream;

        ShellSession(ExecWatch execWatch, OutputStream inputWriter, WebSocketOutputStream outputStream) {
            this.execWatch = execWatch;
            this.inputWriter = inputWriter;
            this.outputStream = outputStream;
        }

        void writeToPod(String payload) throws IOException {
            if (inputWriter == null) {
                return;
            }
            inputWriter.write(payload.getBytes(StandardCharsets.UTF_8));
            inputWriter.flush();
        }

        void close() {
            if (execWatch != null) {
                execWatch.close();
            }
            if (inputWriter != null) {
                try {
                    inputWriter.close();
                } catch (IOException ignored) {
                }
            }
            outputStream.closeSilently();
        }
    }

    private static class WebSocketOutputStream extends OutputStream {
        private final WebSocketSession session;

        WebSocketOutputStream(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            if (!session.isOpen() || len <= 0) {
                return;
            }
            byte[] data = new byte[len];
            System.arraycopy(b, off, data, 0, len);
            session.sendMessage(new TextMessage(new String(data, StandardCharsets.UTF_8)));
        }

        void closeSilently() {
            // no-op but kept for symmetry
        }
    }

    private static class PodShellExecListener implements ExecListener {
        private final WebSocketSession session;

        PodShellExecListener(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void onOpen() {
            // no-op
        }

        @Override
        public void onFailure(Throwable t, ExecListener.Response failureResponse) {
            try {
                if (session.isOpen()) {
                    String reason = t != null ? t.getMessage() : "Unknown error";
                    session.sendMessage(new TextMessage("Shell session failed: " + reason));
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (IOException ignored) {
            }
        }

        @Override
        public void onClose(int code, String reason) {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
