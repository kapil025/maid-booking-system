package com.rupeek.maidbooking.repository;

import com.rupeek.maidbooking.domain.Maid;

import java.util.*;

public interface MaidRepository {
    Maid save(Maid maid);

    Optional<Maid> findById(UUID id);

    List<Maid> findAll();
}
