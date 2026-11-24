package com.rupeek.maidbooking.repository;

import com.rupeek.maidbooking.domain.Maid;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryMaidRepository implements MaidRepository {
    private final Map<UUID, Maid> records = new ConcurrentHashMap<>();

    @Override
    public Maid save(Maid maid) {
        records.put(maid.id(), maid);
        return maid;
    }

    @Override
    public Optional<Maid> findById(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public List<Maid> findAll() {
        return List.copyOf(records.values());
    }
}
