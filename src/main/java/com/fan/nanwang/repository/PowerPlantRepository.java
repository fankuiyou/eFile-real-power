package com.fan.nanwang.repository;

import com.fan.nanwang.entity.PowerPlant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PowerPlantRepository extends JpaRepository<PowerPlant, Long> {

    PowerPlant findByNameAbbreviation(String nameAbbreviation);
}
