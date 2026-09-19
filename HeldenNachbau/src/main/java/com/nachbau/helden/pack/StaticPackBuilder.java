package com.nachbau.helden.pack;

import com.nachbau.helden.HeldenPlugin;
import com.nachbau.helden.state.GlyphAllocator;
import com.nachbau.helden.state.PluginStateManager;
import com.nachbau.helden.state.ServerState;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Baut EIN statisches Resourcepack, das die Koepfe ALLER bekannten Spieler
 * als eigene Font-Glyphen enthaelt (Unicode Private Use Area). Kein eigener
 * Server noetig - das ZIP wird lokal in den Plugin-Ordner geschrieben und
 * muss manuell irgendwo mit HTTPS gehostet werden (z.B. mc-packs.net).
 * Danach die URL + den ausgegebenen SHA1 per
 * "/mchelden pack seturl <url> <sha1>" eintragen.
 *
 * Grund fuer diesen Umweg: moderne Minecraft-Clients laden Resourcepacks nur
 * noch ueber HTTPS. Ein selbstgehosteter Server auf einem Pterodactyl-Slot
 * hat aber weder eigene Domain noch TLS-Zertifikat - ein extern gehosteter,
 * statischer Link mit HTTPS ist der zuverlaessige Weg.
 */
public final class StaticPackBuilder {

    private static final Pattern VALUE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private StaticPackBuilder() {
    }

    public record BuildResult(File file, String sha1Hex, int headCount) {
    }

    /** Baut das komplette Pack synchron - vom Aufrufer aus einem Async-Task starten! */
    public static BuildResult build() throws Exception {
        ServerState state = PluginStateManager.getServerState();
        Set<UUID> known = state.players.keySet();

        File outDir = new File(HeldenPlugin.instance.getDataFolder(), "build");
        outDir.mkdirs();
        File outFile = new File(outDir, "pack.zip");
        int headCount = 0;

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)),
                java.nio.charset.StandardCharsets.UTF_8)) {

            // pack.mcmeta
            writeEntry(zos, "pack.mcmeta", ("""
                    {
                      "pack": {
                        "pack_format": 48,
                        "supported_formats": [42, 64],
                        "description": "HeldenNachbau HUD-Pack"
                      }
                    }
                    """).getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Cyan-Herzen (aus dem mitgelieferten Basis-Pack im Jar uebernehmen)
            copyFromBundledBasePack(zos);

            for (UUID uuid : known) {
                int glyph = GlyphAllocator.glyphFor(uuid);
                BufferedImage head = createHeadTexture(uuid);
                String texturePath = "font/head_" + Integer.toHexString(glyph) + ".png";

                ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
                ImageIO.write(head, "png", pngBytes);
                writeEntry(zos, "assets/minecrafthelden2/textures/" + texturePath, pngBytes.toByteArray());
                headCount++;
            }

            // Ein Font-Provider-Eintrag pro bekanntem Spieler, alle im selben Font "linked"
            String linkedFontJson = buildLinkedFontJson(known);
            writeEntry(zos, "assets/minecrafthelden2/font/linked.json",
                    linkedFontJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            zos.finish();
        }

        byte[] fileBytes = java.nio.file.Files.readAllBytes(outFile.toPath());
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(fileBytes);
        String sha1Hex = HexFormat.of().formatHex(sha1);

        return new BuildResult(outFile, sha1Hex, headCount);
    }

    private static String buildLinkedFontJson(Set<UUID> known) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"providers\":[");
        boolean first = true;
        for (UUID uuid : known) {
            var data = PluginStateManager.getPlayerData(uuid);
            if (data.headGlyph == null) continue;
            String texturePath = "font/head_" + Integer.toHexString(data.headGlyph) + ".png";
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"type\":\"bitmap\",\"file\":\"minecrafthelden2:").append(texturePath)
                    .append("\",\"ascent\":8,\"height\":9,\"chars\":[\"")
                    .append(new String(Character.toChars(data.headGlyph)))
                    .append("\"]}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void copyFromBundledBasePack(ZipOutputStream zos) throws IOException {
        try (InputStream in = HeldenPlugin.instance.getResource("base_pack.zip")) {
            if (in == null) return;
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    // linked.json/linked_head.png aus dem Platzhalter-Pack NICHT uebernehmen -
                    // die werden hier dynamisch pro Spieler ersetzt.
                    if (entry.getName().contains("linked")) continue;
                    if (entry.getName().equals("pack.mcmeta")) continue;
                    writeEntry(zos, entry.getName(), zis.readAllBytes());
                }
            }
        }
    }

    private static void writeEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    // ---------------------------------------------------------- kopf-textur

    private static BufferedImage createHeadTexture(UUID playerId) {
        try {
            String skinUrl = fetchSkinUrl(playerId);
            if (skinUrl == null) return createFallbackTexture();

            HttpRequest request = HttpRequest.newBuilder(URI.create(skinUrl)).GET().build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (skin == null) return createFallbackTexture();

            BufferedImage base = skin.getSubimage(8, 8, 8, 8);
            BufferedImage out = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = out.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(base, 0, 0, null);
            if (skin.getHeight() >= 64) {
                BufferedImage overlay = skin.getSubimage(40, 8, 8, 8);
                graphics.drawImage(overlay, 0, 0, null);
            }
            graphics.dispose();
            return out;
        } catch (Exception e) {
            return createFallbackTexture();
        }
    }

    private static BufferedImage createFallbackTexture() {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(90, 90, 90));
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        return img;
    }

    private static String fetchSkinUrl(UUID uuid) throws IOException, InterruptedException {
        String compactUuid = uuid.toString().replace("-", "");
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + compactUuid)).GET().build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        Matcher texturesMatcher = VALUE_PATTERN.matcher(response.body());
        if (!texturesMatcher.find()) return null;

        String decoded = new String(Base64.getDecoder().decode(texturesMatcher.group(1)));
        Matcher skinMatcher = URL_PATTERN.matcher(decoded);
        if (!skinMatcher.find()) return null;

        return skinMatcher.group(1).replace("\\/", "/");
    }

    // ---------------------------------------------------------- senden

    /** Schickt jedem Spieler die aktuell konfigurierte statische Pack-URL. */
    public static void sendConfiguredPack(Player player) {
        ServerState state = PluginStateManager.getServerState();
        if (state.packUrl == null || state.packSha1 == null) return;

        try {
            byte[] hash = HexFormat.of().parseHex(state.packSha1);
            player.setResourcePack(state.packUrl, hash,
                    Component.text("Bitte lade das Texture Pack für die Herzen!"), false);
        } catch (Exception e) {
            HeldenPlugin.instance.getLogger().warning("Could not send resource pack: " + e.getMessage());
        }
    }
}
