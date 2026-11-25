package com.portfolio.media.internal;

import com.portfolio.config.AppProperties;
import com.portfolio.media.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Stores files in {@code UPLOAD_DIR}. Names are server-generated; anything else is refused, so no path
 * can escape the directory.
 */
@Component
class LocalFileStorage implements FileStorage
{
    static final Pattern SAFE_NAME = Pattern.compile("[a-f0-9-]{36}\\.(png|jpg|webp|gif|mp4|webm|pdf)");

    private final Path root;

    LocalFileStorage(AppProperties properties) throws IOException
    {
        this.root = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void store(String name, byte[] content) throws IOException
    {
        Path target = resolve(name);
        Path temp = Files.createTempFile(root, ".upload-", ".tmp");
        try
        {
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        finally
        {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public void store(String name, InputStream content) throws IOException
    {
        Path target = resolve(name);
        Path temp = Files.createTempFile(root, ".upload-", ".tmp");
        try
        {
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        finally
        {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public Optional<Resource> resource(String name)
    {
        if (!SAFE_NAME.matcher(name).matches())
        {
            return Optional.empty();
        }
        Path path = resolve(name);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    @Override
    public Optional<InputStream> open(String name) throws IOException
    {
        Path path = resolve(name);
        if (!Files.isRegularFile(path))
        {
            return Optional.empty();
        }
        return Optional.of(Files.newInputStream(path));
    }

    @Override
    public void delete(String name) throws IOException
    {
        Files.deleteIfExists(resolve(name));
    }

    @Override
    public boolean exists(String name)
    {
        return SAFE_NAME.matcher(name).matches() && Files.isRegularFile(root.resolve(name));
    }

    private Path resolve(String name)
    {
        if (!SAFE_NAME.matcher(name).matches())
        {
            throw new IllegalArgumentException("Unsafe file name");
        }
        Path path = root.resolve(name).normalize();
        if (!path.getParent().equals(root))
        {
            throw new IllegalArgumentException("Unsafe file name");
        }
        return path;
    }
}
