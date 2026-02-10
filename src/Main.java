import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    public static void main(String[] args) throws IOException {

        int port = 5001; // puerto por defecto

        // si el usuario pasó un puerto por consola, intentamos usarlo
        if (args.length > 0) {
            try {
                int candidate = Integer.parseInt(args[0]);

                // regla del enunciado: debe ser mayor a 1024
                if (candidate > 1024 && candidate <= 65535) {
                    port = candidate;
                } else {
                    System.out.println("Puerto inválido (debe ser >1024 y <=65535). Usando 5001.");
                }
            } catch (NumberFormatException e) {
                System.out.println("El puerto debe ser un número. Usando 5001.");
            }
        }
        // Arranca el servidor
        new Main(port);
    }

    public Main(int port) throws IOException {

        // 1) Creamos el "puerto" donde el servidor se queda escuchando conexiones.
        //    Cualquier cliente que vaya a http://localhost:5001 intentará conectarse aquí.
        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Server ON. Waiting for connections on port" + port + "...");

        // 2) "Conexión constante" del servidor:
        //    Este while(true) significa: "nunca dejes de aceptar clientes".
        //    Si NO está este while, tu servidor atiende SOLO un cliente y ya.
        while (true) {

            // 3) accept() se queda BLOQUEADO esperando un cliente.
            //    - Cuando alguien entra desde el navegador, accept() devuelve un Socket.
            //    - Ese Socket representa la conexión con ESE cliente en específico.
            Socket socket = serverSocket.accept();

            System.out.println("New client connected: " + socket.getInetAddress());

            // 4) Multi-hilos:
            //    Para que el servidor pueda atender a MUCHOS clientes a la vez,
            //    no atendemos al cliente aquí mismo.
            //    En su lugar, creamos un hilo para ese cliente.
            //
            //    El hilo ejecuta el método run() de ClientHandler.
            ClientHandler handler = new ClientHandler(socket);
            Thread thread = new Thread(handler);

            // 5) start() arranca el hilo.
            //    IMPORTANTE:
            //    - start() crea un hilo nuevo y llama run() por debajo.
            //    - si llamaras run() directamente, NO sería multi-hilo (se ejecuta en el mismo hilo).
            thread.start();

            // 6) Volvemos al inicio del while(true) para aceptar otro cliente.
            //    Mientras tanto, el cliente anterior sigue siendo atendido por su hilo.
        }
    }

    // -------------------------------------------------------------------------
    // Esta clase atiende 1 SOLO cliente (1 Socket) en un hilo aparte.
    // -------------------------------------------------------------------------
    static class ClientHandler implements Runnable {

        // Guardamos el socket del cliente para poder leer y escribir con ese cliente.
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));

                // 1) Leer request line (una sola solicitud)
                String requestLine = br.readLine();
                if (requestLine == null) {
                    socket.close();
                    return;
                }

                System.out.println("\n--- REQUEST START (" + socket.getInetAddress() + ") ---");
                System.out.println(requestLine);

                // 2) Leer y mostrar headers (hasta línea vacía)
                String headerLine;
                while ((headerLine = br.readLine()) != null && !headerLine.isEmpty()) {
                    System.out.println(headerLine);
                }
                System.out.println("--- REQUEST END ---\n");

                // 3) Parsear request line
                String[] parts = requestLine.split(" ");
                if (parts.length < 3) {
                    bw.write("HTTP/1.0 400 Bad Request\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.flush();
                    socket.close();
                    return;
                }

                String method = parts[0];
                String path = parts[1];
                String version = parts[2];

                if (!method.equals("GET")) {
                    bw.write("HTTP/1.0 405 Method Not Allowed\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.flush();
                    socket.close();
                    return;
                }

                if (path.equals("/")) {
                    path = "/index.html";
                }

                System.out.println("METHOD = " + method);
                System.out.println("PATH   = " + path);
                System.out.println("HTTP   = " + version);

                // 4) Buscar archivo dentro de www
                String filePath = "www" + path;
                File file = new File(filePath);

                // AQUÍ detectas el MIME
                String contentType;

                if (path.endsWith(".html") || path.endsWith(".htm")) {
                    contentType = "text/html";
                } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (path.endsWith(".gif")) {
                    contentType = "image/gif";
                } else {
                    contentType = "application/octet-stream";
                }

                System.out.println("MIME   = " + contentType);

                if (!file.exists() || file.isDirectory()) {

                    File errorFile = new File("www/404.html");

                    // Si el archivo 404.html existe, lo leemos; si no, usamos un fallback
                    String errorBody = "<html><body><h1>404 Not Found</h1></body></html>";

                    if (errorFile.exists() && !errorFile.isDirectory()) {
                        BufferedReader fr = new BufferedReader(new FileReader(errorFile));
                        StringBuilder sb = new StringBuilder();
                        String l;
                        while ((l = fr.readLine()) != null) {
                            sb.append(l).append("\n");
                        }
                        fr.close();
                        errorBody = sb.toString();
                    }

                    int len = errorBody.getBytes().length;

                    bw.write("HTTP/1.0 404 Not Found\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.write("<html><body><h1>404 Not Found</h1></body></html>");
                    bw.flush();
                    socket.close();
                    return;
                }

                // 5) Enviar respuesta según el MIME
                if (contentType.equals("text/html")) {

                    // Leer HTML como texto
                    BufferedReader fileReader = new BufferedReader(new FileReader(file));
                    StringBuilder bodyBuilder = new StringBuilder();
                    String line;
                    while ((line = fileReader.readLine()) != null) {
                        bodyBuilder.append(line).append("\n");
                    }
                    fileReader.close();

                    String body = bodyBuilder.toString();
                    int contentLength = body.getBytes().length;

                    // Responder HTML
                    bw.write("HTTP/1.0 200 OK\r\n");
                    bw.write("Content-Type: " + contentType + "\r\n");
                    bw.write("Content-Length: " + contentLength + "\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.write(body);
                    bw.flush();

                } else if (contentType.equals("image/jpeg") || contentType.equals("image/gif")) {

                    // Leer imagen como bytes
                    byte[] fileBytes = new byte[(int) file.length()];
                    FileInputStream fis = new FileInputStream(file);
                    int bytesRead = fis.read(fileBytes);
                    fis.close();

                    if (bytesRead == -1) {
                        bw.write("HTTP/1.0 500 Internal Server Error\r\n");
                        bw.write("Connection: close\r\n");
                        bw.write("\r\n");
                        bw.flush();
                        socket.close();
                        return;
                    }

                    // Enviar headers primero (texto)
                    bw.write("HTTP/1.0 200 OK\r\n");
                    bw.write("Content-Type: " + contentType + "\r\n");
                    bw.write("Content-Length: " + fileBytes.length + "\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.flush();

                    // Enviar body binario (bytes)
                    os.write(fileBytes);
                    os.flush();

                } else {
                    // Extensión no soportada (opcional)
                    bw.write("HTTP/1.0 415 Unsupported Media Type\r\n");
                    bw.write("Connection: close\r\n");
                    bw.write("\r\n");
                    bw.flush();
                }

                socket.close();



            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("socket closed")) {
                    return;
                }
                System.out.println("Error with client " + socket.getInetAddress() + ": " + e.getMessage());
            }
        }
    }

    // Pruebas:
    // - http://localhost:5001/
    // - desde otro dispositivo en tu red: http://TU_IP:5001/
}