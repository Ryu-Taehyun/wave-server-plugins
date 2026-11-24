package kr.wave.core.util;

import kr.wave.core.WaveCore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** playerdata 폴더 + global.yml + config.yml 을 backups/<이름>.zip 으로 묶음 */
public final class Backup {

    private Backup() {
    }

    public static File zip(WaveCore plugin, String name) throws IOException {
        File dir = new File(plugin.getDataFolder(), "backups");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("backups 폴더를 만들 수 없습니다");
        File out = new File(dir, name + ".zip");
        File tmp = new File(dir, name + ".zip.part");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(tmp))) {
            File[] files = plugin.store().folder().listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) for (File f : files) put(z, f, "playerdata/" + f.getName());
            if (plugin.store().globalFile().exists()) put(z, plugin.store().globalFile(), "global.yml");
            File cfg = new File(plugin.getDataFolder(), "config.yml");
            if (cfg.exists()) put(z, cfg, "config.yml");
        } catch (IOException e) {
            Files.deleteIfExists(tmp.toPath());
            throw e;
        }
        Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return out;
    }

    private static void put(ZipOutputStream z, File f, String name) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        Files.copy(f.toPath(), z);
        z.closeEntry();
    }
}
