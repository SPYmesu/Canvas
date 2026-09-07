package io.canvasmc.canvas.util.version;

import com.destroystokyo.paper.util.VersionFetcher;
import io.papermc.paper.ServerBuildInfo;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.jspecify.annotations.NullMarked;

import static net.kyori.adventure.text.Component.text;

/**
 * Replacement for {@link com.destroystokyo.paper.PaperVersionFetcher}, which resolves the build distance
 * against PaperMC's API and cannot report anything meaningful for a fork. Keeps the layout upstream uses
 * and highlights the build information following the JustKiwi branding.
 */
@NullMarked
public class CanvasVersionFetcher implements VersionFetcher {

    private static final String FORK_NAME = "JustKiwi.ru";

    @Override
    public long getCacheTime() {
        return TimeUnit.DAYS.toMillis(1L); // the build information never changes at runtime, but the cache starts out empty and has to be computed once
    }

    @Override
    public Component getVersionMessage() {
        return Component.textOfChildren(
            text("You are running fork version for ", NamedTextColor.WHITE),
            text(FORK_NAME, NamedTextColor.DARK_GREEN)
        );
    }

    @Override
    public Component getFullOutMessage() {
        final ServerBuildInfo buildInfo = ServerBuildInfo.buildInfo();
        final String full = buildInfo.asString(ServerBuildInfo.StringRepresentation.VERSION_FULL);
        // VERSION_FULL ends with the build time in brackets, split it back off so it can be coloured on its own
        final String buildTimeSuffix = " (" + buildInfo.buildTime().truncatedTo(ChronoUnit.SECONDS) + ")";
        final boolean hasBuildTime = full.endsWith(buildTimeSuffix);

        final TextComponent.Builder builder = text();
        builder.append(text("This server is running ", NamedTextColor.WHITE));
        builder.append(text(buildInfo.brandName(), NamedTextColor.GOLD));
        builder.append(text(" version ", NamedTextColor.WHITE));
        builder.append(text(hasBuildTime ? full.substring(0, full.length() - buildTimeSuffix.length()) : full, NamedTextColor.GOLD));
        if (hasBuildTime) {
            builder.append(text(" (", NamedTextColor.WHITE));
            builder.append(text(buildTimeSuffix.substring(2, buildTimeSuffix.length() - 1), NamedTextColor.GOLD));
            builder.append(text(")", NamedTextColor.WHITE));
        }
        builder.append(text(" (Implementing API version ", NamedTextColor.WHITE));
        builder.append(text(Bukkit.getBukkitVersion(), NamedTextColor.GOLD));
        builder.append(text(")", NamedTextColor.WHITE));
        builder.append(Component.newline());
        builder.append(this.getVersionMessage());

        final Component message = builder.build();
        return message
            .hoverEvent(Component.translatable("chat.copy.click", NamedTextColor.WHITE))
            .clickEvent(ClickEvent.copyToClipboard(PlainTextComponentSerializer.plainText().serialize(message)));
    }
}
