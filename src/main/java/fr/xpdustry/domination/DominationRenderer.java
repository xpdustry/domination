/*
 * DominationPlugin, a "capture the zone" like gamemode plugin.
 *
 * Copyright (C) 2022  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package fr.xpdustry.domination;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import cloud.commandframework.meta.*;
import fr.xpdustry.distributor.api.plugin.*;
import fr.xpdustry.distributor.api.util.*;
import java.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;

public final class DominationRenderer implements PluginListener {

  private static final Seq<Effect> EFFECTS = Seq.with(Fx.mine, Fx.mineBig, Fx.mineHuge);

  private final Interval interval = new Interval();
  private final Map<Zone, WorldLabel> labels = new HashMap<>();
  private final Set<Player> viewers = new HashSet<>();
  private final DominationPlugin domination;

  public DominationRenderer(final DominationPlugin domination) {
    this.domination = domination;
  }

  @Override
  public void onPluginLoad() {
    MoreEvents.subscribe(EventType.PlayEvent.class, event -> {
      labels.clear();
    });

    MoreEvents.subscribe(EventType.PlayerLeave.class, event -> {
      viewers.remove(event.player);
    });

    domination.getClientCommandManager().command(domination.getClientCommandManager()
      .commandBuilder("domination")
      .literal("zone")
      .literal("view")
      .meta(CommandMeta.DESCRIPTION, "Enable/Disable domination zone view mode.")
      .permission("fr.xpdustry.domination.map.zone.view")
      .handler(ctx -> {
        final var player = ctx.getSender().getPlayer();
        if (!this.viewers.add(player)) {
          this.viewers.remove(player);
        }
        ctx.getSender().sendMessage(
          Strings.format("You @ zone viewing.", viewers.contains(player) ? "enabled" : "disabled")
        );
      }));
  }

  @Override
  public void onPluginUpdate() {
    if (interval.get(Time.toSeconds / 6) && Vars.state.isPlaying()) {
      if (domination.isEnabled()) {
        // Graphics
        Groups.player.forEach(this::drawZoneCircles);
        labels.forEach((zone, label) -> {
          label.text(Strings.format("[#@]@%", zone.getTeam().color, zone.getCapture()));
        });

        // HUD text
        final var builder = new StringBuilder(100)
          .append("Time remaining > ").append(Strings.formatMillis(domination.getState().getRemainingTime().toMillis()));

        // Leaderboard
        domination.getState().getLeaderboard().entrySet()
          .stream()
          .sorted(Comparator.comparingDouble(Map.Entry::getValue))
          .forEach(e -> {
            builder
                .append("\n[#").append(e.getKey().color).append(']')
                .append(e.getKey() == Team.derelict ? "Unclaimed" : Strings.capitalize(e.getKey().name))
                .append("[] > ").append(e.getValue() / domination.getState().getZones().size()).append('%');
            }
          );

        Call.setHudText(builder.toString());

        // Update labels
        final var zones = new HashSet<>(domination.getState().getZones());
        final var entries = labels.entrySet().iterator();
        while (entries.hasNext()) {
          final var entry = entries.next();
          if (!zones.remove(entry.getKey())) {
            entries.remove();
            entry.getValue().remove();
            Call.removeWorldLabel(entry.getValue().id());
          }
        }
        for (final var zone : zones) {
          final var label = WorldLabel.create();
          label.text("???%");
          label.z(Layer.flyingUnit);
          label.flags((byte) (WorldLabel.flagOutline | WorldLabel.flagBackground));
          label.fontSize(2F);
          label.set(zone.getX(), zone.getY());
          label.add();
          labels.put(zone, label);
        }
      } else {
        for (final var viewer : viewers) {
          drawZoneCircles(viewer);
          for (final var zone : this.domination.getState().getZones()) {
            Call.label(viewer.con(), "[#" + zone.getTeam().color + "]" + Iconc.star, 1F / 6, zone.getX(), zone.getY());
          }
        }
      }
    }
  }

  private void drawZoneCircles(final Player player) {
    for (final var zone : this.domination.getState().getZones()) {
      final var circle = Geometry.regPoly((int) (Mathf.pi * (zone.getRadius() / Vars.tilesize)), zone.getRadius());
      Geometry.iteratePolygon((px, py) -> {
        Call.effect(player.con(), EFFECTS.random(), px + zone.getX(), py + zone.getY(), 0, zone.getTeam().color);
      }, circle);
    }
  }
}
