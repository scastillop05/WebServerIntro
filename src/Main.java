import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Mini servidor web básico:
 * - TCP con ServerSocket/Socket
 * - Concurrencia (un hilo por solicitud: 1 request por conexión)
 * - Lectura de request HTTP (línea + headers)
 * - Respuestas HTTP válidas (CRLF)
 * - Servir archivos: HTML, JPG, GIF + MIME correcto
 * - 404 con página de error desde archivo
 * - Cierre correcto de recursos (try-with-resources)
 */
public class Main {

    public static void main(String[] args) throws IOException {

        // Puerto por defecto
        int port = 5001;

        // Si pasan un puerto por consola, intentamos usarlo:
        // Ej: java Main 8080
        if (args.length > 0) {
            try {
                int candidate = Integer.parseInt(args[0]);

                // Regla del enunciado: puerto > 1024 y dentro del rango válido
                if (candidate > 1024 && candidate <= 65535) {
                    port = candidate;
                } else {
                    System.out.println("Puerto inválido (debe ser >1024 y <=65535). Usando 5001.");
                }
            } catch (NumberFormatException e) {
                System.out.println("El puerto debe ser un número. Usando 5001.");
            }
        }

        // Arranca el servidor con el puerto ya definido
        new Main(port);
    }

    public Main(int port) throws IOException {

        // ServerSocket = “puerto” donde el servidor escucha conexiones TCP
        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Server ON. Waiting for connections on port " + port + "...");

        // Servidor “continuo”: nunca deja de aceptar clientes
        while (true) {

            // accept() se queda bloqueado hasta que alguien se conecte
            Socket socket = serverSocket.accept();

            System.out.println("New client connected: " + socket.getInetAddress());

            // Multi-hilos:
            // Por cada conexión (que en nuestro caso equivale a 1 request),
            // creamos un hilo que atiende al cliente y responde.
            ClientHandler handler = new ClientHandler(socket);
            Thread thread = new Thread(handler);
            thread.start();

            // El hilo principal vuelve a accept() para aceptar el siguiente cliente.
        }
    }

    /**
     * Atiende a 1 cliente en un hilo separado.
     * En este servidor implementamos “1 request por conexión”:
     * - Leemos una request line
     * - Leemos headers
     * - Respondemos
     * - Cerramos (automático con try-with-resources)
     */
    static class ClientHandler implements Runnable {

        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            // try-with-resources: todo lo declarado aquí se cierra automáticamente
            // al salir del bloque (incluso si hay excepción).
            try (
                    Socket client = socket;
                    InputStream is = client.getInputStream();
                    OutputStream os = client.getOutputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os))
            ) {

                // =========================
                // 1) Leer la request line
                // =========================
                // Ejemplos:
                //   GET / HTTP/1.1
                //   GET /index.html HTTP/1.1
                String requestLine = br.readLine();

                // Si viene null, el cliente cerró sin enviar nada
                if (requestLine == null) {
                    return;
                }

                System.out.println("\n--- REQUEST START (" + socket.getInetAddress() + ") ---");
                System.out.println(requestLine);

                // =========================
                // 2) Leer y mostrar headers
                // =========================
                // HTTP termina headers con una línea vacía
                String headerLine;
                while ((headerLine = br.readLine()) != null && !headerLine.isEmpty()) {
                    System.out.println(headerLine);
                }
                System.out.println("--- REQUEST END ---\n");

                // =========================
                // 3) Parsear la request line
                // =========================
                // Request line: METHOD PATH VERSION
                // Ej: GET /hola.html HTTP/1.1
                String[] parts = requestLine.split(" ");

                // Si no tiene al menos 3 partes => request mal formada
                if (parts.length < 3) {
                    bw.write("HTTP/1.0 400 Bad Request\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.flush();
                    return;
                }

                String method = parts[0];
                String path = parts[1];
                String version = parts[2];

                // Solo permitimos GET (como pide el enunciado)
                if (!method.equals("GET")) {
                    bw.write("HTTP/1.0 405 Method Not Allowed\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.flush();
                    return;
                }

                // Si piden "/" servimos index.html
                if (path.equals("/")) {
                    path = "/index.html";
                }

                System.out.println("METHOD = " + method);
                System.out.println("PATH   = " + path);
                System.out.println("HTTP   = " + version);

                // =========================
                // 4) Mapear path -> archivo
                // =========================
                // Todo sale de la carpeta "www"
                // Ej: /hola.html => www/hola.html
                String filePath = "www" + path;
                File file = new File(filePath);

                // =========================
                // 5) Detectar MIME (Content-Type)
                // =========================
                String contentType;
                if (path.endsWith(".html") || path.endsWith(".htm")) {
                    contentType = "text/html";
                } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (path.endsWith(".gif")) {
                    contentType = "image/gif";
                } else {
                    // Tipo genérico para extensiones que no soportamos explícitamente
                    contentType = "application/octet-stream";
                }

                System.out.println("MIME   = " + contentType);

                // =========================
                // 6) Si no existe => 404 con archivo www/404.html
                // =========================
                if (!file.exists() || file.isDirectory()) {

                    File errorFile = new File("www/404.html");

                    // fallback por si 404.html no existe
                    String errorBody = "<html><body><h1>404 Not Found</h1></body></html>";

                    // Si existe el 404.html, lo cargamos
                    if (errorFile.exists() && !errorFile.isDirectory()) {
                        BufferedReader fr = new BufferedReader(new FileReader(errorFile));
                        StringBuilder sb = new StringBuilder();
                        String l;
                        while ((l = fr.readLine()) != null) sb.append(l).append("\n");
                        fr.close();
                        errorBody = sb.toString();
                    }

                    int len = errorBody.getBytes().length;

                    bw.write("HTTP/1.0 404 Not Found\r\n");
                    bw.write("Content-Type: text/html\r\n");
                    bw.write("Content-Length: " + len + "\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.write(errorBody);
                    bw.flush();
                    return;
                }

                // =========================
                // 7) Responder según el tipo de archivo
                // =========================

                // (A) HTML: se envía como texto (BufferedWriter)
                if (contentType.equals("text/html")) {

                    BufferedReader fileReader = new BufferedReader(new FileReader(file));
                    StringBuilder bodyBuilder = new StringBuilder();
                    String line;
                    while ((line = fileReader.readLine()) != null) {
                        bodyBuilder.append(line).append("\n");
                    }
                    fileReader.close();

                    String body = bodyBuilder.toString();
                    int contentLength = body.getBytes().length;

                    bw.write("HTTP/1.0 200 OK\r\n");
                    bw.write("Content-Type: " + contentType + "\r\n");
                    bw.write("Content-Length: " + contentLength + "\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.write(body);
                    bw.flush();

                }
                // (B) Imágenes: se envían como bytes (OutputStream)
                else if (contentType.equals("image/jpeg") || contentType.equals("image/gif")) {

                    byte[] fileBytes = new byte[(int) file.length()];
                    FileInputStream fis = new FileInputStream(file);
                    int bytesRead = fis.read(fileBytes);
                    fis.close();

                    // Si por alguna razón no se leyó nada
                    if (bytesRead == -1) {
                        bw.write("HTTP/1.0 500 Internal Server Error\r\n");
                        bw.write("Connection: close\r\n");
                        bw.write("\r\n");
                        bw.flush();
                        return;
                    }

                    // Primero mandamos headers (texto)
                    bw.write("HTTP/1.0 200 OK\r\n");
                    bw.write("Content-Type: " + contentType + "\r\n");
                    bw.write("Content-Length: " + fileBytes.length + "\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.flush();

                    // Luego mandamos el body binario (bytes)
                    os.write(fileBytes);
                    os.flush();

                }
                // (C) Otros tipos: no soportados explícitamente
                else {
                    bw.write("HTTP/1.0 415 Unsupported Media Type\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.flush();
                }

            } catch (IOException e) {
                // Si hay error con este cliente, no tumbamos el servidor
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("socket closed")) {
                    return;
                }
                System.out.println("Error with client " + socket.getInetAddress() + ": " + e.getMessage());
            }
        }
    }

    // Pruebas:
    // - http://localhost:5001/
    // - http://localhost:5001/test.jpg
    // - http://localhost:5001/test.gif
    // - http://localhost:5001/aaa.html
}