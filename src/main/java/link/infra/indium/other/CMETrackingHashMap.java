package link.infra.indium.other;

import com.google.common.collect.Maps;
import net.minecraft.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class CMETrackingHashMap<K, V> implements Map<K, V> {
	private final Map<K, V> backing = Maps.newHashMap();
	public boolean isIterating = false;
	public BlockEntity ent = null;

	private void check() {
		if (isIterating) {
			throw new RuntimeException("CME detected");
		}
	}

	@Override
	public int size() {
		return backing.size();
	}

	@Override
	public boolean isEmpty() {
		return backing.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return backing.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return backing.containsValue(value);
	}

	@Override
	public V get(Object key) {
		return backing.get(key);
	}

	@Nullable
	@Override
	public V put(K key, V value) {
		check();
		return backing.put(key, value);
	}

	@Override
	public V remove(Object key) {
		check();
		return backing.remove(key);
	}

	@Override
	public void putAll(@NotNull Map<? extends K, ? extends V> m) {
		check();
		backing.putAll(m);
	}

	@Override
	public void clear() {
		check();
		backing.clear();
	}

	@NotNull
	@Override
	public Set<K> keySet() {
		return Collections.unmodifiableSet(backing.keySet());
	}

	@NotNull
	@Override
	public Collection<V> values() {
		return Collections.unmodifiableCollection(backing.values());
	}

	@NotNull
	@Override
	public Set<Entry<K, V>> entrySet() {
		return Collections.unmodifiableSet(backing.entrySet());
	}

	@Override
	public boolean equals(Object o) {
		return backing.equals(o);
	}

	@Override
	public int hashCode() {
		return backing.hashCode();
	}

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		return backing.getOrDefault(key, defaultValue);
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		backing.forEach(action);
	}

	@Override
	public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		check();
		backing.replaceAll(function);
	}

	@Nullable
	@Override
	public V putIfAbsent(K key, V value) {
		check();
		return backing.putIfAbsent(key, value);
	}

	@Override
	public boolean remove(Object key, Object value) {
		check();
		return backing.remove(key, value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		check();
		return backing.replace(key, oldValue, newValue);
	}

	@Nullable
	@Override
	public V replace(K key, V value) {
		check();
		return backing.replace(key, value);
	}

	@Override
	public V computeIfAbsent(K key, @NotNull Function<? super K, ? extends V> mappingFunction) {
		check();
		return backing.computeIfAbsent(key, mappingFunction);
	}

	@Override
	public V computeIfPresent(K key, @NotNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		check();
		return backing.computeIfPresent(key, remappingFunction);
	}

	@Override
	public V compute(K key, @NotNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		check();
		return backing.compute(key, remappingFunction);
	}

	@Override
	public V merge(K key, @NotNull V value, @NotNull BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		check();
		return backing.merge(key, value, remappingFunction);
	}
}
