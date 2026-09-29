package net.tfminecraft.woodworking;

import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockito.*;

class TestSupport {
  @TempDir Path dir;
  ServerMock server;
  Woodworking plugin;
  ItemAPI api;
  MockedStatic<TLibs> tlibs;
  Map<java.lang.reflect.Field, Object> cache = new HashMap<>();

  @BeforeEach
  void baseSetup() throws Exception {
    server = MockBukkit.mock();
    plugin = mock(Woodworking.class);
    Woodworking.plugin = plugin;
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    when(plugin.getDataFolder()).thenReturn(dir.toFile());
    when(plugin.getServer()).thenReturn(server);
    when(plugin.isEnabled()).thenReturn(true);
    when(plugin.getName()).thenReturn("Woodworking");
    when(plugin.namespace()).thenReturn("woodworking");
    net.tfminecraft.woodworking.loader.QualityLoader.get().clear();
    net.tfminecraft.woodworking.loader.CategoryLoader.get().clear();
    net.tfminecraft.woodworking.loader.ProjectLoader.get().clear();
    api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemFromPath(anyString())).thenReturn(null);
    when(api.getChecker().getAsStringPath(any())).thenReturn(null);
    tlibs = mockStatic(TLibs.class);
    tlibs.when(TLibs::getItemAPI).thenReturn(api);
    for (Class<?> type : List.of(net.tfminecraft.woodworking.cache.Cache.class))
      for (var f : type.getFields()) cache.put(f, f.get(null));
  }

  @AfterEach
  void baseCleanup() throws Exception {
    tlibs.close();
    MockBukkit.unmock();
    Woodworking.plugin = null;
    for (var e : cache.entrySet()) e.getKey().set(null, e.getValue());
  }

  ItemStack item(Material m) {
    return new ItemStack(m);
  }

  Path yaml(String name, String text) throws Exception {
    var p = dir.resolve(name);
    Files.writeString(p, text);
    return p;
  }

  static Object invoke(Object target, String name, Class<?>[] types, Object... args)
      throws Exception {
    Class<?> c = target instanceof Class<?> cls ? cls : target.getClass();
    java.lang.reflect.Method m;
    while (true) {
      try {
        m = c.getDeclaredMethod(name, types);
        break;
      } catch (NoSuchMethodException ex) {
        c = c.getSuperclass();
        if (c == null) throw ex;
      }
    }
    m.setAccessible(true);
    return m.invoke(target instanceof Class<?> ? null : target, args);
  }

  org.bukkit.configuration.file.YamlConfiguration config(String text) throws Exception {
    var c = new org.bukkit.configuration.file.YamlConfiguration();
    c.loadFromString(text);
    return c;
  }
}
