# HTTP Web Server

**Sebastian Castillo**
**A00170732**

## Descripción

Este proyecto consiste en la implementación de un servidor web básico en Java utilizando ServerSocket y Socket. El servidor escucha conexiones TCP, procesa solicitudes HTTP mediante el método GET y sirve archivos estáticos desde una carpeta local.

El servidor opera de forma continua y maneja múltiples clientes utilizando un hilo independiente por cada solicitud.

---

## Funcionalidades principales

- Puerto configurable mayor a 1024.
- Servidor en ejecución continua.
- Un hilo por cada solicitud HTTP.
- Procesamiento del método GET.
- Impresión en consola de la línea de solicitud y los encabezados.
- Construcción de respuestas HTTP válidas usando CRLF.
- Servicio de archivos HTML, JPG y GIF.
- Detección correcta del tipo MIME.
- Manejo de error 404 con página personalizada.
- Cierre adecuado de recursos usando `try-with-resources`.

---

## Estructura del proyecto
```
├── src
│   └── Main.java
└── www
    ├── 404.html
    ├── hola.html
    ├── index.html
    ├── test.gif
    └── test.jpg
```
---

# Cómo ejecutar el servidor paso a paso

## 1. Abrir la terminal

Ubicarse en la carpeta donde se encuentra el archivo `Main.java`.

Ejemplo:

```
cd ruta/del/proyecto
```

---

## 2. Verificar estructura del proyecto

Debe existir la siguiente estructura mínima:

```
.
├── Main.java
└── www/
    ├── index.html
    └── 404.html
```

Si no existe la carpeta `www`, crearla y agregar al menos un `index.html`.

---

## 3. Compilar el programa

Ejecutar:

```
javac Main.java
```

Si no aparecen errores, el programa se compiló correctamente.

---

## 4. Ejecutar el servidor

Para usar el puerto por defecto (5001):

```
java Main
```

Para usar un puerto específico (mayor a 1024):

```
java Main 8080
```

En consola debe aparecer:

```
Server ON. Waiting for connections on port 5001...
```

---

## 5. Probar en el navegador

Abrir el navegador y escribir:

```
http://localhost:5001/
```

También se pueden probar:

```
http://localhost:5001/index.html
http://localhost:5001/test.jpg
http://localhost:5001/test.gif
http://localhost:5001/noexiste.html
```

---

## 6. Detener el servidor

En la terminal donde está corriendo el servidor, presionar:

```
Ctrl + C
```

Esto detiene la ejecución del programa.
