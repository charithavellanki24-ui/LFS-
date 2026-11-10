
import java.util.*;
import java.io.*;
import java.net.*;
import com.sun.net.httpserver.*;
import java.nio.file.*;

class Report implements Serializable {
    private static final long serialVersionUID = 1L;

    String type, item, description, location, date;
    String contact, status = "Open";

    Report(String type, String item, String description,
           String location, String date, String contact) {
        this.type = type;
        this.item = item;
        this.description = description;
        this.location = location;
        this.date = date;
        this.contact = contact;
    }
}

public class LostFoundSystem {

    static ArrayList<Report> list = new ArrayList<Report>();
    static final String DATA_FILE = "reports.dat";

    static void save() {
        try (ObjectOutputStream out =
             new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            out.writeObject(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    static void load() {
        try (ObjectInputStream in =
             new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            list = (ArrayList<Report>) in.readObject();
        } catch (Exception e) {
            list = new ArrayList<Report>();
        }
    }

    static String param(String body, String key) {
        try {
            for (String part : body.split("&")) {
                String[] pair = part.split("=", 2);

                if (pair.length == 2 && pair[0].equals(key)) {
                    return URLDecoder.decode(pair[1], "UTF-8");
                }
            }
        } catch (Exception e) {
            return "";
        }

        return "";
    }

    static byte[] readBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;

        while ((count = ex.getRequestBody().read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }

        return out.toByteArray();
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    static void send(HttpExchange ex, String data,
                     String contentType) throws IOException {

        byte[] bytes = data.getBytes("UTF-8");

        ex.getResponseHeaders().set(
            "Content-Type", contentType + "; charset=UTF-8");

        ex.getResponseHeaders().set(
            "Access-Control-Allow-Origin", "*");

        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static String json(Report r) {
        return "{\"type\":\"" + escape(r.type) +
            "\",\"item\":\"" + escape(r.item) +
            "\",\"description\":\"" + escape(r.description) +
            "\",\"location\":\"" + escape(r.location) +
            "\",\"date\":\"" + escape(r.date) +
            "\",\"contact\":\"" + escape(r.contact) +
            "\",\"status\":\"" + escape(r.status) + "\"}";
    }

    static void startServer() throws Exception {

        HttpServer server = HttpServer.create(
            new InetSocketAddress(8080), 0);

        server.createContext("/api/reports", ex -> {

            StringBuilder result = new StringBuilder("[");

            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    result.append(",");
                }

                result.append(json(list.get(i)));
            }

            result.append("]");

            send(ex, result.toString(), "application/json");
        });

        server.createContext("/api/create", ex -> {

            String body = new String(readBody(ex), "UTF-8");

            Report r = new Report(
                param(body, "type"),
                param(body, "item"),
                param(body, "description"),
                param(body, "location"),
                param(body, "date"),
                param(body, "contact")
            );

            if (r.contact.trim().isEmpty() ||
                r.item.trim().isEmpty()) {

                send(ex,
                    "{\"message\":\"Required fields missing\"}",
                    "application/json");
                return;
            }

            list.add(r);
            save();

            send(ex,
                "{\"message\":\"Report Created\"}",
                "application/json");
        });

        server.createContext("/api/resolve", ex -> {

            String body = new String(readBody(ex), "UTF-8");

            int index;

            try {
                index = Integer.parseInt(param(body, "index"));
            } catch (Exception e) {
                send(ex, "{\"message\":\"Invalid index\"}",
                     "application/json");
                return;
            }

            if (index >= 0 && index < list.size()) {

                list.get(index).status = "Resolved";
                save();

                send(ex,
                    "{\"message\":\"Updated\"}",
                    "application/json");

            } else {
                send(ex,
                    "{\"message\":\"Not found\"}",
                    "application/json");
            }
        });

        server.createContext("/api/contact", ex -> {

            String body = new String(readBody(ex), "UTF-8");

            int index;
            String contact = param(body, "contact");

            try {
                index = Integer.parseInt(param(body, "index"));
            } catch (Exception e) {
                send(ex,
                    "{\"message\":\"Invalid details\"}",
                    "application/json");
                return;
            }

            if (index >= 0 && index < list.size()
                    && !contact.trim().isEmpty()) {

                list.get(index).contact = contact;
                save();

                send(ex,
                    "{\"message\":\"Contact Updated\"}",
                    "application/json");

            } else {
                send(ex,
                    "{\"message\":\"Invalid details\"}",
                    "application/json");
            }
        });

        server.createContext("/", ex -> {

            String path = URLDecoder.decode(
                ex.getRequestURI().getPath(), "UTF-8");

            if (path.equals("/")) {
                path = "/index.html";
            }

            Path root = Paths.get(".").toRealPath();
            Path file = root.resolve(path.substring(1)).normalize();

            if (!file.startsWith(root) ||
                !Files.exists(file) ||
                Files.isDirectory(file)) {

                send(ex, "File Not Found", "text/plain");
                return;
            }

            String name = file.toString();

            String type = name.endsWith(".css") ? "text/css" :
                          name.endsWith(".js") ?
                          "application/javascript" :
                          "text/html";

            byte[] content = Files.readAllBytes(file);
            String data = new String(content, "UTF-8");

            send(ex, data, type);
        });

        server.setExecutor(null);
        server.start();

        System.out.println("Lost and Found System Started!");
        System.out.println("Open: http://localhost:8080");
    }

    public static void main(String[] args) throws Exception {
        load();
        startServer();
    }
}
