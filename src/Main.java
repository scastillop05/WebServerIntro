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
            // Este método corre dentro del hilo del cliente.
            // Aquí va TODO lo que haces con el cliente: leer request y mandar response.
            try {

                // 1) Obtenemos los "tubos" de comunicación con el cliente:
                //    - InputStream  => lo que el cliente NOS ENVÍA (request)
                //    - OutputStream => lo que nosotros le ENVIAMOS al cliente (response)
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                // 2) Convertimos los streams a lectura/escritura por texto:
                //    - BufferedReader: leer líneas de texto (headers HTTP vienen en líneas)
                //    - BufferedWriter: escribir texto (response HTTP)
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));

                // 3) "Conexión constante" del CLIENTE:
                //    Este while(true) permite que el MISMO cliente haga varias peticiones
                //    usando la misma conexión (si el navegador mantiene la conexión).
                //
                //    Nota: El navegador puede cerrar cuando quiera. Si la cierra, readLine() da null.
                while (true) {

                    // 4) La primera línea del request HTTP suele ser:
                    //    GET / HTTP/1.1
                    //    o
                    //    GET /algo HTTP/1.1
                    //
                    //    Si el cliente cerró la conexión, readLine() devuelve null.
                    String requestLine = br.readLine();

                    if (requestLine == null) {
                        // Significa: el cliente cerró la conexión.
                        System.out.println("Client disconnected: " + socket.getInetAddress());
                        break; // salimos del while del cliente
                    }

                    // 5) Imprimimos la línea principal del request para ver qué pidió el cliente.
                    System.out.println("\n--- REQUEST START (" + socket.getInetAddress() + ") ---");
                    System.out.println(requestLine);

                    // 6) Después de la primera línea, vienen los headers, por ejemplo:
                    //    Host: localhost:5001
                    //    User-Agent: ...
                    //    Accept: ...
                    //
                    //    En HTTP, los headers terminan cuando aparece una LÍNEA VACÍA.
                    String headerLine;
                    while ((headerLine = br.readLine()) != null && !headerLine.isEmpty()) {
                        System.out.println(headerLine);
                    }
                    System.out.println("--- REQUEST END ---\n");

                    // 7) Ahora respondemos.
                    //    El navegador espera un response HTTP con:
                    //    - Status line: HTTP/1.1 200 OK
                    //    - Headers
                    //    - Línea vacía
                    //    - Body (HTML)
                    //
                    //    Vamos a armar un body sencillo:
                    String body = "<html><body>Hola Mundo</body></html>";

                    // 8) Escribimos el response.
                    //    OJO: Si no mandas Content-Length, algunos clientes igual funcionan,
                    //    pero otros pueden quedarse esperando. Aquí lo mandamos para que sea más “formal”.
                    //
                    //    Content-Length debe ser la cantidad de bytes del body.
                    //    (En ASCII simple coincide con caracteres, pero en general es bytes.)
                    int contentLength = body.getBytes().length;

                    bw.write("HTTP/1.1 200 OK\r\n");                     // línea de estado
                    bw.write("Content-type: text/html\r\n");            // tipo de contenido
                    bw.write("Content-Length: " + contentLength + "\r\n"); // tamaño del body
                    bw.write("Connection: keep-alive\r\n");             // le decimos: mantén conexión
                    bw.write("\r\n");                                   // línea vacía: fin headers
                    bw.write(body);                                     // body HTML
                    bw.flush();                                         // IMPORTANTÍSIMO: envía todo

                    // 9) Gracias al while(true) del cliente, NO cerramos aún.
                    //    El cliente puede volver a mandar otra petición en la misma conexión.
                }

                // 10) Cuando salimos del while (cliente cerró), cerramos el socket.
                socket.close();

            } catch (IOException e) {
                // Si hay error con ESE cliente, no tumbamos el servidor completo.
                System.out.println("Error with client " + socket.getInetAddress() + ": " + e.getMessage());
            }
        }
    }

    // Pruebas:
    // - http://localhost:5001/
    // - desde otro dispositivo en tu red: http://TU_IP:5001/
}