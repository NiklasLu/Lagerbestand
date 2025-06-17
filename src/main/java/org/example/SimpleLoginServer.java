package org.example;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.sql.Connection;
import java.sql.PreparedStatement;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.*;

public class SimpleLoginServer {
    public static void main(String[] args) throws IOException {
        //updateKlartextPasswoerterZuHash(); //war nur zum hashen der bestehenden User
        HttpServer server = HttpServer.create(new InetSocketAddress(3000), 0);
        server.createContext("/login", new LoginHandler());
        server.createContext("/bestand", new BestandHandler());
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
        server.createContext("/zutaten", new ZutatenHandler());
        //server.createContext("/zutat/hinzufuegen", new ZutatHinzufuegenHandler());
        server.createContext("/produkt/anlegen", new ProduktAnlegenHandler());
        server.createContext("/produkt/liste", new ProduktListeHandler());
        server.createContext("/produkte", new ProduktHandler());
        server.createContext("/produkte/neu", new NeuesProduktHandler());
        server.createContext("/produkte/loeschen", new ProduktLoeschenHandler());
        server.createContext("/bestand/eintragen", new BestandEintragenHandler());
        //server.createContext("/kasse", new VerkaufsStatistikHandler());
        server.createContext("/produkte/hinzufuegen", new ProduktHinzufuegenHandler());
        server.createContext("/produkt/loeschen", new ProduktLoeschenHandler());
        server.createContext("/kasse", new KasseHandler());
        server.createContext("/verkaufte_artikel/kasse", new VerkaufteArtikelHandler());
        server.createContext("/rezepte", new RezepteHandler());                    // GET
        server.createContext("/rezepte/hinzufuegen", new RezeptHinzufuegenHandler());  // POST
        server.createContext("/rezepte/aktualisieren", new RezeptAktualisierenHandler()); // POST
        server.createContext("/rezepte/loeschen", new RezeptLoeschenHandler());
        server.createContext("/produkte/aktualisieren", new BestandAktualisierenHandler());// DELETE
        server.createContext("/produkte/bestand", new ProduktBestandAktualisierenHandler());
        server.createContext("/", new StaticFileHandler("src/main/java/org/example"));
        server.setExecutor(null);
        server.start();
        File file = new File("abweichungen.txt");
        if (file.exists()) {
            System.out.println("⚠️ Es liegen möglicherweise Abweichungen zur Prüfung vor (siehe abweichungen.txt)");
        }
        System.out.println("✅ Server läuft auf http://localhost:3000");
    }

    static class ProduktBestandAktualisierenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "PATCH, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            int id = Integer.parseInt(query.split("=")[1]);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
                String json = reader.readLine();
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                int bestand = obj.get("bestand").getAsInt();

                try (Connection conn = VerbindungDB.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("UPDATE produkte SET aktueller_bestand = ? WHERE id = ?");
                    stmt.setInt(1, bestand);
                    stmt.setInt(2, id);
                    stmt.executeUpdate();

                    String response = "{\"success\": true}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                String error = "{\"success\": false, \"message\": \"Fehler beim Aktualisieren\"}";
                exchange.sendResponseHeaders(500, error.length());
                exchange.getResponseBody().write(error.getBytes());
                exchange.getResponseBody().close();
            }
        }
    }


    static class BestandAktualisierenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
                int produktId = data.get("id").getAsInt();
                int neuerBestand = data.get("aktueller_bestand").getAsInt();

                try (Connection conn = VerbindungDB.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("UPDATE produkte SET aktueller_bestand = ? WHERE id = ?");
                    stmt.setInt(1, neuerBestand);
                    stmt.setInt(2, produktId);
                    stmt.executeUpdate();
                }

                String response = "{\"success\": true}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (Exception e) {
                e.printStackTrace();
                String response = "{\"success\": false}";
                exchange.sendResponseHeaders(500, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    static class ProduktBestandUpdateHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            int id = Integer.parseInt(exchange.getRequestURI().getQuery().split("=")[1]);
            int neuerBestand = json.get("bestand").getAsInt();

            try (Connection conn = VerbindungDB.getConnection()) {
                PreparedStatement stmt = conn.prepareStatement("UPDATE produkte SET aktueller_bestand = ? WHERE id = ?");
                stmt.setInt(1, neuerBestand);
                stmt.setInt(2, id);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            String response = "{\"success\": true}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        }
    }


    static class RezeptAktualisierenHandler implements HttpHandler {
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
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);

                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    int id = json.get("id").getAsInt();
                    double neueMenge = json.get("menge").getAsDouble();

                    try (Connection conn = VerbindungDB.getConnection()) {
                        PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE rezepte SET menge_pro_stueck = ? WHERE id = ?"
                        );
                        stmt.setDouble(1, neueMenge);
                        stmt.setInt(2, id);
                        stmt.executeUpdate();

                        String response = "{\"success\": true}";
                        exchange.sendResponseHeaders(200, response.length());
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }

                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    String response = "{\"success\": false}";
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

    static class RezeptLoeschenHandler implements HttpHandler {
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
                String query = exchange.getRequestURI().getQuery(); // z. B. id=3
                int id = Integer.parseInt(query.split("=")[1]);

                try (Connection conn = VerbindungDB.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM rezepte WHERE id = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();

                    String response = "{\"success\": true}";
                    exchange.sendResponseHeaders(200, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
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

    static class RezeptHinzufuegenHandler implements HttpHandler {
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
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {

                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    String produkt = json.get("produkt").getAsString();
                    String zutat = json.get("zutat").getAsString();
                    double menge = json.get("menge").getAsDouble();

                    try (Connection conn = VerbindungDB.getConnection()) {
                        // Nur die Zutat muss existieren
                        PreparedStatement zutatStmt = conn.prepareStatement(
                            "SELECT id FROM produkte WHERE name = ?");
                        zutatStmt.setString(1, zutat);
                        ResultSet zutatRs = zutatStmt.executeQuery();

                        if (!zutatRs.next()) {
                            throw new SQLException("Zutat nicht gefunden");
                        }

                        int zutatId = zutatRs.getInt("id");

                        // Rezept hinzufügen mit produkt_name
                        PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO rezepte (produkt_name, zutat_id, menge_pro_stueck) VALUES (?, ?, ?)");
                        insert.setString(1, produkt);
                        insert.setInt(2, zutatId);
                        insert.setDouble(3, menge);
                        insert.executeUpdate();

                        JsonObject responseJson = new JsonObject();
                        responseJson.addProperty("success", true);
                        responseJson.addProperty("message", "Rezept hinzugefügt");
                        String response = responseJson.toString();

                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.getBytes().length);
                        exchange.getResponseBody().write(response.getBytes());
                        exchange.getResponseBody().close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String error = "{\"success\": false, \"message\": \"Fehler beim Hinzufügen\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, error.getBytes().length);
                    exchange.getResponseBody().write(error.getBytes());
                    exchange.getResponseBody().close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class RezepteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (Connection conn = VerbindungDB.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT r.id, r.produkt_name AS produkt, p.name AS zutat, r.menge_pro_stueck " +
                            "FROM rezepte r " +
                            "JOIN produkte p ON r.zutat_id = p.id"
                    );
                    ResultSet rs = stmt.executeQuery();

                    JsonArray array = new JsonArray();
                    while (rs.next()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("id", rs.getInt("id"));
                        obj.addProperty("produkt", rs.getString("produkt"));
                        obj.addProperty("zutat", rs.getString("zutat"));
                        obj.addProperty("menge", rs.getDouble("menge_pro_stueck"));
                        array.add(obj);
                    }

                    String response = new Gson().toJson(array);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    String error = "{\"error\":\"Datenbankfehler\"}";
                    exchange.sendResponseHeaders(500, error.length());
                    exchange.getResponseBody().write(error.getBytes());
                    exchange.getResponseBody().close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }



    static class VerkaufteArtikelHandler implements HttpHandler {
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
                JsonArray result = new JsonArray();
                JsonObject cappuccino = new JsonObject();
                cappuccino.addProperty("name", "Cappuccino");
                cappuccino.addProperty("anzahl", 14);
                result.add(cappuccino);

                JsonObject espresso = new JsonObject();
                espresso.addProperty("name", "Espresso");
                espresso.addProperty("anzahl", 8);
                result.add(espresso);

                JsonObject latte = new JsonObject();
                latte.addProperty("name", "Latte Macchiato");
                latte.addProperty("anzahl", 11);
                result.add(latte);

                String json = new Gson().toJson(result);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length());
                exchange.getResponseBody().write(json.getBytes());
                exchange.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }


    static class KasseHandler implements HttpHandler {
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
                try (Connection conn = VerbindungDB.getConnection()) {
                    // 1. Verkauf simulieren
                    Map<String, Integer> verkaufteProdukte = new HashMap<>();
                    verkaufteProdukte.put("Cappuccino", 14);
                    verkaufteProdukte.put("Latte Macchiato", 11);
                    verkaufteProdukte.put("Espresso", 8);

                    // 2. Rezepte auslesen
                    Map<String, Map<String, Double>> rezeptMap = new HashMap<>();
                    PreparedStatement rezepteStmt = conn.prepareStatement("SELECT * FROM rezepte");
                    ResultSet rezepteRs = rezepteStmt.executeQuery();
                    while (rezepteRs.next()) {
                        String produkt = rezepteRs.getString("produkt_name");
                        String zutat = rezepteRs.getString("zutat_name");
                        double menge = rezepteRs.getDouble("menge_pro_stueck");
                        rezeptMap.putIfAbsent(produkt, new HashMap<>());
                        rezeptMap.get(produkt).put(zutat, menge);
                    }

                    // 3. Verbrauch berechnen
                    Map<String, Double> gesamtVerbrauch = new HashMap<>();
                    for (Map.Entry<String, Integer> entry : verkaufteProdukte.entrySet()) {
                        String produkt = entry.getKey();
                        int anzahl = entry.getValue();
                        if (!rezeptMap.containsKey(produkt)) continue;
                        for (Map.Entry<String, Double> zutatEntry : rezeptMap.get(produkt).entrySet()) {
                            String zutat = zutatEntry.getKey();
                            double menge = zutatEntry.getValue() * anzahl;
                            gesamtVerbrauch.put(zutat, gesamtVerbrauch.getOrDefault(zutat, 0.0) + menge);
                        }
                    }

                    // 4. aktuellen Bestand holen
                    Map<String, Integer> aktuellerBestand = new HashMap<>();
                    Statement bestandsStmt = conn.createStatement();
                    ResultSet bestandsRs = bestandsStmt.executeQuery("SELECT * FROM bestand ORDER BY gespeichert_am DESC LIMIT 1");
                    if (bestandsRs.next()) {
                        ResultSetMetaData meta = bestandsRs.getMetaData();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            String spalte = meta.getColumnName(i);
                            if (spalte.equals("id") || spalte.equals("gespeichert_am")) continue;
                            aktuellerBestand.put(spalte, bestandsRs.getInt(spalte));
                        }
                    }

                    // 5. Verbrauch anwenden (nur ganze Einheiten abziehen)
                    Map<String, Integer> neuerBestand = new HashMap<>(aktuellerBestand);
                    for (Map.Entry<String, Double> entry : gesamtVerbrauch.entrySet()) {
                        String zutat = entry.getKey();
                        double verbrauch = entry.getValue();
                        int abziehen = (int) verbrauch; // nur ganze Einheiten
                        neuerBestand.put(zutat, Math.max(0, aktuellerBestand.getOrDefault(zutat, 0) - abziehen));
                    }

                    // 6. neuen Bestand speichern – nur wenn Änderungen da sind
                    if (!neuerBestand.isEmpty()) {
                        StringBuilder sql = new StringBuilder("INSERT INTO bestand (");
                        StringBuilder fragezeichen = new StringBuilder();
                        List<Integer> werte = new ArrayList<>();
                        for (String zutat : neuerBestand.keySet()) {
                            sql.append(zutat).append(",");
                            fragezeichen.append("?,");
                            werte.add(neuerBestand.get(zutat));
                        }
                        sql.setLength(sql.length() - 1);
                        fragezeichen.setLength(fragezeichen.length() - 1);
                        sql.append(") VALUES (").append(fragezeichen).append(")");

                        PreparedStatement insert = conn.prepareStatement(sql.toString());
                        for (int i = 0; i < werte.size(); i++) {
                            insert.setInt(i + 1, werte.get(i));
                        }
                        insert.executeUpdate();
                    }

                    // 7. Rückmeldung
                    JsonObject json = new JsonObject();
                    json.addProperty("success", true);
                    json.addProperty("message", "Kasse verarbeitet. Lagerbestand aktualisiert.");

                    String response = new Gson().toJson(json);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    String response = "{\"success\": false, \"message\": \"Fehler bei Kasse-Verarbeitung\"}";
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




    static class ProduktHinzufuegenHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "UTF-8"));
                JsonObject obj = JsonParser.parseString(reader.readLine()).getAsJsonObject();
                String name = obj.get("name").getAsString();
                int max = obj.get("max_kapazitaet").getAsInt();

                try (Connection conn = VerbindungDB.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO produkte (name, max_kapazitaet) VALUES (?, ?)");
                    stmt.setString(1, name);
                    stmt.setInt(2, max);
                    stmt.executeUpdate();
                    String response = "{\"success\": true}";
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


    //FileHandler
    public static class StaticFileHandler implements HttpHandler {

        private final String basePath;

        public StaticFileHandler(String basePath) {
            this.basePath = basePath;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/Chef.html";
            }

            File file = new File(basePath + path);
            if (!file.exists() || file.isDirectory()) {
                String notFound = "404 Not Found";
                exchange.sendResponseHeaders(404, notFound.length());
                OutputStream os = exchange.getResponseBody();
                os.write(notFound.getBytes());
                os.close();
                return;
            }

            String contentType = Files.probeContentType(Path.of(file.getPath()));
            exchange.getResponseHeaders().add("Content-Type", contentType != null ? contentType : "text/html");
            exchange.sendResponseHeaders(200, file.length());

            OutputStream os = exchange.getResponseBody();
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            fis.close();
            os.close();
        }
    }



    //Neuer server
    static class ProduktHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (Connection conn = VerbindungDB.getConnection()) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM produkte");

                    JsonArray arr = new JsonArray();
                    while (rs.next()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("id", rs.getInt("id"));
                        obj.addProperty("name", rs.getString("name"));
                        obj.addProperty("max_kapazitaet", rs.getInt("max_kapazitaet"));
                        arr.add(obj);
                    }
                    String response = arr.toString();
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

    static class NeuesProduktHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
                String json = reader.readLine();
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                String name = obj.get("name").getAsString();
                int max = obj.get("max_menge").getAsInt();

                try (Connection conn = VerbindungDB.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO produkte (name, " +
                        "max_menge) VALUES (?, ?)");
                    stmt.setString(1, name);
                    stmt.setInt(2, max);
                    stmt.executeUpdate();
                    String response = "{\"success\":true}";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class ProduktLoeschenHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery(); // id=2
                if (query == null || !query.startsWith("id=")) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }

                int produktId = Integer.parseInt(query.split("=")[1]);

                try (Connection conn = VerbindungDB.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM produkte WHERE id = ?");
                    stmt.setInt(1, produktId);
                    stmt.executeUpdate();

                    String response = "{\"success\": true}";
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

    static class BestandEintragenHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
                JsonObject obj = JsonParser.parseString(reader.readLine()).getAsJsonObject();
                int produktId = obj.get("produktId").getAsInt();
                int menge = obj.get("menge").getAsInt();
                String kommentar = obj.get("kommentar").getAsString();

                try (Connection conn = VerbindungDB.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO bestand_history (produkt_id, menge, kommentar) VALUES (?, ?, ?)");
                    stmt.setInt(1, produktId);
                    stmt.setInt(2, menge);
                    stmt.setString(3, kommentar);
                    stmt.executeUpdate();
                    String response = "{\"success\":true}";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class VerkaufsStatistikHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (Connection conn = VerbindungDB.getConnection()) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT produkt, SUM(menge) AS verkauft FROM verkauf GROUP BY produkt");

                    JsonArray array = new JsonArray();
                    while (rs.next()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("produkt", rs.getString("produkt"));
                        obj.addProperty("verkauft", rs.getInt("verkauft"));
                        array.add(obj);
                    }
                    String response = array.toString();
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }



    // Neue Produktverwaltung für Zutaten
//    static class ProduktHandler implements HttpHandler {
//        @Override
//        public void handle(HttpExchange exchange) throws IOException {
//            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
//            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
//            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
//
//            String method = exchange.getRequestMethod();
//
//            if ("OPTIONS".equalsIgnoreCase(method)) {
//                exchange.sendResponseHeaders(204, -1);
//                return;
//            }
//
//            String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
//            String dbUser = "javauser";
//            String dbPass = "passwort123";
//
//            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
//                if ("GET".equalsIgnoreCase(method)) {
//                    Statement stmt = conn.createStatement();
//                    ResultSet rs = stmt.executeQuery("SELECT name FROM produkte ORDER BY name ASC");
//                    JsonArray array = new JsonArray();
//                    while (rs.next()) {
//                        array.add(rs.getString("name"));
//                    }
//                    String response = new Gson().toJson(array);
//                    exchange.getResponseHeaders().set("Content-Type", "application/json");
//                    exchange.sendResponseHeaders(200, response.length());
//                    exchange.getResponseBody().write(response.getBytes());
//                    exchange.getResponseBody().close();
//                }
//
//                else if ("POST".equalsIgnoreCase(method)) {
//                    BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
//                    String json = reader.readLine();
//                    String produktName = new Gson().fromJson(json, JsonObject.class).get("name").getAsString();
//
//                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO produkte (name) VALUES (?)");
//                    stmt.setString(1, produktName);
//                    stmt.executeUpdate();
//
//                    String response = "{\"success\": true}";
//                    exchange.getResponseHeaders().set("Content-Type", "application/json");
//                    exchange.sendResponseHeaders(200, response.length());
//                    exchange.getResponseBody().write(response.getBytes());
//                    exchange.getResponseBody().close();
//                }
//
//                else if ("DELETE".equalsIgnoreCase(method)) {
//                    String query = exchange.getRequestURI().getQuery();
//                    String produktName = java.net.URLDecoder.decode(query.split("=")[1], "UTF-8");
//
//                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM produkte WHERE name = ?");
//                    stmt.setString(1, produktName);
//                    stmt.executeUpdate();
//
//                    String responseDelete = "{\"success\": true}";
//                    exchange.getResponseHeaders().set("Content-Type", "application/json");
//                    exchange.sendResponseHeaders(200, responseDelete.length());
//                    exchange.getResponseBody().write(responseDelete.getBytes());
//                    exchange.getResponseBody().close();
//                }
//
//                else {
//                    exchange.sendResponseHeaders(405, -1);
//                }
//
//            } catch (SQLException e) {
//                e.printStackTrace();
//                exchange.sendResponseHeaders(500, -1);
//            }
//        }
//    }


    //Liste erweitern
    static class ProduktListeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
            try (Connection conn = DriverManager.getConnection(url, "javauser", "passwort123")) {
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM produkte");

                JsonArray arr = new JsonArray();
                while (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", rs.getInt("id"));
                    obj.addProperty("name", rs.getString("name"));
                    arr.add(obj);
                }

                String response = new Gson().toJson(arr);
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            } catch (SQLException e) {
                e.printStackTrace();
                String response = "{\"error\": \"DB Fehler\"}";
                exchange.sendResponseHeaders(500, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            }
        }
    }

    //Produkte hinzufuegen
    static class ProduktAnlegenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
            JsonObject json = JsonParser.parseString(reader.readLine()).getAsJsonObject();
            String name = json.get("name").getAsString();

            String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
            try (Connection conn = DriverManager.getConnection(url, "javauser", "passwort123")) {
                PreparedStatement stmt = conn.prepareStatement("INSERT INTO produkte (name) VALUES (?)");
                stmt.setString(1, name);
                stmt.executeUpdate();

                String response = "{\"success\": true}";
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            } catch (SQLException e) {
                e.printStackTrace();
                String response = "{\"success\": false}";
                exchange.sendResponseHeaders(500, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            }
        }
    }



    //Zutaten hinzufügen
    static class ZutatHinzufuegenHandler implements HttpHandler {
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
                String name = JsonParser.parseString(sb.toString()).getAsJsonObject().get("name").getAsString();

                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    PreparedStatement stmt = conn.prepareStatement("INSERT IGNORE INTO zutaten (name) VALUES (?)");
                    stmt.setString(1, name.toLowerCase());
                    stmt.executeUpdate();

                    String response = "{\"success\": true}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    String response = "{\"success\": false}";
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



    //Zutaten handler
    static class ZutatenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
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
                    ResultSet rs = stmt.executeQuery("SELECT name FROM zutaten ORDER BY id ASC");

                    JsonArray array = new JsonArray();
                    while (rs.next()) {
                        array.add(rs.getString("name"));
                    }

                    String response = array.toString();
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
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
                String kommentar = json.has("kommentar") ? json.get("kommentar").getAsString() : "";


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
                                out.println("Kommentar: " + kommentar);
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