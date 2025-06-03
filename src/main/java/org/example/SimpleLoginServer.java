package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.sql.*;
import com.google.gson.*;

public class SimpleLoginServer {
    public static void main(String[] args) throws IOException {
        //updateKlartextPasswoerterZuHash(); //war nur zum hashen der bestehenden User
        HttpServer server = HttpServer.create(new InetSocketAddress(3000), 0);
        server.createContext("/login", new LoginHandler());
        server.createContext("/bestand", new BestandHandler());
        server.setExecutor(null);
        server.start();
        server.createContext("/pruefen", new PruefHandler());
        //server.createContext("/abweichungen", new AbweichungHandler());
        server.createContext("/abweichungen/clear", new AbweichungClearHandler());
        server.createContext("/abweichung/korrigieren", new AbweichungKorrigierenHandler());
        server.createContext("/benutzer", new BenutzerHandler());
        server.createContext("/benutzer/anlegen", new BenutzerAnlegenHandler());
        server.createContext("/historie.html", new HtmlSeitenHandler("src/main/java/org/example/Bestands-Historie.html"));
        server.createContext("/bestand/historie", new BestandHistorieHandler());
        server.createContext("/abweichungen", new AbweichungHandler());
        server.createContext("/registrierung.html", new HtmlSeitenHandler("src/main/java/org" +
            "/example/registrieren.html"));
        server.createContext("/benutzer/loeschen", new BenutzerLoeschenHandler());
        File file = new File("abweichungen.txt");
        if (file.exists()) {
            System.out.println("⚠️ Es liegen möglicherweise Abweichungen zur Prüfung vor (siehe abweichungen.txt)");
        }
        System.out.println("✅ Server läuft auf http://localhost:3000");
    }


    static class BenutzerLoeschenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery(); // z.B. ?id=3
                if (query == null || !query.contains("id=")) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }

                int id = Integer.parseInt(query.split("=")[1]);

                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM benutzer WHERE id = ?");
                    stmt.setInt(1, id);
                    int deleted = stmt.executeUpdate();

                    String response = "{\"success\": " + (deleted > 0) + "}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();

                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class HtmlSeitenHandler implements HttpHandler {
        private final String dateipfad;

        public HtmlSeitenHandler(String dateipfad) {
            this.dateipfad = dateipfad;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");

            File file = new File(dateipfad);
            if (!file.exists()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] inhalt = Files.readAllBytes(file.toPath());
            exchange.sendResponseHeaders(200, inhalt.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(inhalt);
            }
        }
    }






    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS erlauben
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                String username = json.get("username").getAsString();
                String password = json.get("password").getAsString();

                boolean success = checkLogin(username, password);

                String response = "{\"success\":" + success + "}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private boolean checkLogin(String username, String password) {
            String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
            String dbUser = "javauser";
            String dbPass = "passwort123";

            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM benutzer WHERE name = ? AND passwort = ?"
                );
                stmt.setString(1, username);
                stmt.setString(2, hashPassword(password)); // 🔐 Passwort vor dem Vergleich hashen
                ResultSet rs = stmt.executeQuery();

                boolean gefunden = rs.next();
                System.out.println("Login-Versuch für '" + username + "' → gefunden: " + gefunden);
                return gefunden;

            } catch (SQLException e) {
                System.out.println("Fehler bei DB-Verbindung:");
                e.printStackTrace();
                return false;
            }
        }
    }

    static class PruefHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS-Header
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                int kaffee = json.get("kaffee").getAsInt();
                int milch = json.get("milch").getAsInt();
                int eier = json.get("eier").getAsInt();
                int mehl = json.get("mehl").getAsInt();

                // Letzten Original-Bestand holen
                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM bestand ORDER BY gespeichert_am DESC LIMIT 1");

                    if (rs.next()) {
                        int origId = rs.getInt("id");
                        boolean stimmt = (
                            rs.getInt("kaffee") == kaffee &&
                                rs.getInt("milch") == milch &&
                                rs.getInt("eier") == eier &&
                                rs.getInt("mehl") == mehl
                        );

                        PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO pruefung (original_id, kaffee, milch, eier, mehl, stimmt_ueberein) VALUES (?, ?, ?, ?, ?, ?)"
                        );
                        insert.setInt(1, origId);
                        insert.setInt(2, kaffee);
                        insert.setInt(3, milch);
                        insert.setInt(4, eier);
                        insert.setInt(5, mehl);
                        insert.setBoolean(6, stimmt);
                        insert.executeUpdate();

                        String response = stimmt
                                          ? "{\"success\": true, \"message\": \"Bestände stimmen überein.\"}"
                                          : "{\"success\": false, \"message\": \"Abweichung festgestellt. Chef wurde benachrichtigt.\"}";

                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();

                        if (!stimmt) {
                            // Nachricht an den "Chef" speichern
                            try (FileWriter fw = new FileWriter("abweichungen.txt", true);
                                 BufferedWriter bw = new BufferedWriter(fw);
                                 PrintWriter out = new PrintWriter(bw)) {
                                out.println("❌ Abweichung festgestellt am " + new java.util.Date());
                                out.println("Original: Kaffee=" + rs.getInt("kaffee") + ", Milch=" + rs.getInt("milch") + ", Eier=" + rs.getInt("eier") + ", Mehl=" + rs.getInt("mehl"));
                                out.println("Gezählt:  Kaffee=" + kaffee + ", Milch=" + milch + ", Eier=" + eier + ", Mehl=" + mehl);
                                out.println("-----------------------------------------------------");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class AbweichungHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS-Header
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM pruefung WHERE stimmt_ueberein = false ORDER BY timestamp DESC"
                    );
                    ResultSet rs = stmt.executeQuery();

                    JsonArray array = new JsonArray();
                    while (rs.next()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("zeit", rs.getString("timestamp"));
                        obj.addProperty("kaffee", rs.getInt("kaffee"));
                        obj.addProperty("milch", rs.getInt("milch"));
                        obj.addProperty("eier", rs.getInt("eier"));
                        obj.addProperty("mehl", rs.getInt("mehl"));
                        array.add(obj);
                    }

                    String response = new Gson().toJson(array);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    static class AbweichungClearHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM pruefung WHERE stimmt_ueberein = false"
                    );
                    int rows = stmt.executeUpdate();

                    String response = "{\"success\": true, \"deleted\": " + rows + "}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    static class AbweichungKorrigierenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery(); // z.B. id=7
                int id = Integer.parseInt(query.split("=")[1]);

                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    // Hole Korrekturwerte
                    PreparedStatement select = conn.prepareStatement("SELECT * FROM pruefung WHERE id = ?");
                    select.setInt(1, id);
                    ResultSet rs = select.executeQuery();

                    if (rs.next()) {
                        int originalId = rs.getInt("original_id");

                        // Aktualisiere Bestand
                        PreparedStatement update = conn.prepareStatement(
                            "UPDATE bestand SET kaffee = ?, milch = ?, eier = ?, mehl = ? WHERE id = ?"
                        );
                        update.setInt(1, rs.getInt("kaffee"));
                        update.setInt(2, rs.getInt("milch"));
                        update.setInt(3, rs.getInt("eier"));
                        update.setInt(4, rs.getInt("mehl"));
                        update.setInt(5, originalId);
                        update.executeUpdate();

                        // Lösche die Abweichung
                        PreparedStatement delete = conn.prepareStatement("DELETE FROM pruefung WHERE id = ?");
                        delete.setInt(1, id);
                        delete.executeUpdate();

                        String response = "{\"success\": true, \"message\": \"Bestand aktualisiert.\"}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class BestandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS erlauben
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();

                int kaffee = json.get("kaffee").getAsInt();
                int milch = json.get("milch").getAsInt();
                int eier = json.get("eier").getAsInt();
                int mehl = json.get("mehl").getAsInt();

                String kommentar = json.has("kommentar") ? json.get("kommentar").getAsString() : "kein Kommentar";
                boolean success = speichereBestand(kaffee, milch, eier, mehl, kommentar);


                String response = "{\"success\":" + success + "}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }

            } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Aktuellsten Lagerbestand abrufen
                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM bestand ORDER BY gespeichert_am DESC LIMIT 1");

                    if (rs.next()) {
                        JsonObject json = new JsonObject();
                        json.addProperty("kaffee", rs.getInt("kaffee"));
                        json.addProperty("milch", rs.getInt("milch"));
                        json.addProperty("eier", rs.getInt("eier"));
                        json.addProperty("mehl", rs.getInt("mehl"));

                        String response = new Gson().toJson(json);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length());
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                    } else {
                        String response = "{\"error\": \"Kein Bestand gefunden\"}";
                        exchange.sendResponseHeaders(404, response.length());
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    String response = "{\"error\": \"Datenbankfehler\"}";
                    exchange.sendResponseHeaders(500, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }

            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private boolean speichereBestand(int kaffee, int milch, int eier, int mehl, String kommentar) {
            String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
            String dbUser = "javauser";
            String dbPass = "passwort123";

            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO bestand (kaffee, milch, eier, mehl, kommentar) VALUES (?, ?, ?, ?, ?)"
                );
                stmt.setInt(1, kaffee);
                stmt.setInt(2, milch);
                stmt.setInt(3, eier);
                stmt.setInt(4, mehl);
                stmt.setString(5, kommentar);
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    static class BenutzerAnlegenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                String name = json.get("name").getAsString();
                String passwort = json.get("passwort").getAsString();
                String vorname = json.get("vorname").getAsString();
                String nachname = json.get("nachname").getAsString();

                String gehashtesPasswort = hashPassword(passwort);

                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO benutzer (name, passwort, vorname, nachname) VALUES (?, ?, ?, ?)"
                    );
                    stmt.setString(1, name);
                    stmt.setString(2, gehashtesPasswort);
                    stmt.setString(3, vorname);
                    stmt.setString(4, nachname);
                    stmt.executeUpdate();

                    String response = "{\"success\": true}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    String response = "{\"success\": false, \"error\": \"DB-Fehler\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }


    static class BenutzerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT id, name, vorname, nachname FROM benutzer");

                    JsonArray array = new JsonArray();
                    while (rs.next()) {
                        JsonObject user = new JsonObject();
                        user.addProperty("id", rs.getInt("id"));
                        user.addProperty("name", rs.getString("name"));
                        user.addProperty("vorname", rs.getString("vorname"));
                        user.addProperty("nachname", rs.getString("nachname"));
                        array.add(user);
                    }

//                    JsonArray array = new JsonArray();
//                    while (rs.next()) {
//                        JsonObject user = new JsonObject();
//                        user.addProperty("id", rs.getInt("id"));
//                        user.addProperty("name", rs.getString("name"));
//                        user.addProperty("vorname", rs.getString("vorname"));
//                        user.addProperty("nachname", rs.getString("nachname"));
//                        array.add(user);
//                    }

                    String response = new Gson().toJson(array);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    String response = "{\"error\":\"DB-Fehler\"}";
                    exchange.sendResponseHeaders(500, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
//    static class BenutzerAnlegenHandler implements HttpHandler {
//        @Override
//        public void handle(HttpExchange exchange) throws IOException {
//            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
//            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
//            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
//
//            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
//                exchange.sendResponseHeaders(204, -1);
//                return;
//            }
//
//            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
//                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
//                StringBuilder sb = new StringBuilder();
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    sb.append(line);
//                }
//
//                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
//                String name = json.get("name").getAsString();
//                String passwort = json.get("passwort").getAsString();
//
//                // 🔐 Passwort hashen
//                String gehashtesPasswort = hashPassword(passwort);
//
//                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
//                String dbUser = "javauser";
//                String dbPass = "passwort123";
//
//                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
//                    PreparedStatement stmt = conn.prepareStatement(
//                        "INSERT INTO benutzer (name, passwort) VALUES (?, ?)"
//                    );
//                    stmt.setString(1, name);
//                    stmt.setString(2, gehashtesPasswort); // ← Hash speichern
//                    stmt.executeUpdate();
//
//                    String response = "{\"success\": true}";
//                    exchange.getResponseHeaders().set("Content-Type", "application/json");
//                    exchange.sendResponseHeaders(200, response.length());
//                    try (OutputStream os = exchange.getResponseBody()) {
//                        os.write(response.getBytes());
//                    }
//                } catch (SQLException e) {
//                    e.printStackTrace();
//                    String response = "{\"success\": false, \"error\": \"DB-Fehler\"}";
//                    exchange.getResponseHeaders().set("Content-Type", "application/json");
//                    exchange.sendResponseHeaders(500, response.length());
//                    try (OutputStream os = exchange.getResponseBody()) {
//                        os.write(response.getBytes());
//                    }
//                }
//            } else {
//                exchange.sendResponseHeaders(405, -1);
//            }
//        }
//    }

    public static String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Hashen des Passworts", e);
        }
    }

    public static void updateKlartextPasswoerterZuHash() {
        String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
        String dbUser = "javauser";
        String dbPass = "passwort123";

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
            PreparedStatement select = conn.prepareStatement("SELECT id, passwort FROM benutzer");
            ResultSet rs = select.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String original = rs.getString("passwort");

                if (original.length() < 64) { // vermutlich noch nicht gehasht
                    String hash = hashPassword(original);
                    PreparedStatement update = conn.prepareStatement("UPDATE benutzer SET passwort = ? WHERE id = ?");
                    update.setString(1, hash);
                    update.setInt(2, id);
                    update.executeUpdate();

                    System.out.println("✔ Benutzer-ID " + id + " → Passwort gehasht.");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    static class BestandHistorieHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
            String dbUser = "javauser";
            String dbPass = "passwort123";

            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM bestand ORDER BY gespeichert_am DESC");

                JsonArray array = new JsonArray();
                while (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("gespeichert_am", rs.getString("gespeichert_am"));
                    obj.addProperty("kaffee", rs.getInt("kaffee"));
                    obj.addProperty("milch", rs.getInt("milch"));
                    obj.addProperty("eier", rs.getInt("eier"));
                    obj.addProperty("mehl", rs.getInt("mehl"));
                    obj.addProperty("kommentar", rs.getString("kommentar"));
                    array.add(obj);
                }

                String response = new Gson().toJson(array);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (SQLException e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }



}