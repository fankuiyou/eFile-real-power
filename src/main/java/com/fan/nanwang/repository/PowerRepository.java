package com.fan.nanwang.repository;

import com.fan.nanwang.entity.Power;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface PowerRepository extends JpaRepository<Power, Long> {

    List<Power> findByNameAbbreviationAndDate(String nameAbbreviation, String date);

    @Query(value = "select max(date) from power where region = :region", nativeQuery = true)
    String findMaxDateByRegion(@Param("region") String region);

    @Query(value = "select pp.id, p.date, right(p.date, 5) time, p.power from power p " +
            "LEFT JOIN power_plant pp on p.name_abbreviation = pp.name_abbreviation " +
            "where pp.id = :ycId and date BETWEEN concat(:date, ' 00:15') and concat(DATE_ADD(:date, INTERVAL 1 DAY), ' 00:00') " +
            "ORDER BY date", nativeQuery = true)
    List<Map> findByYcIdAndDate(@Param("ycId") Long ycId, @Param("date") String date);

    @Query(value = "select left(min(date), 10) from power", nativeQuery = true)
    String findMinDate();

    @Query(value = "select left(max(date), 10) from power", nativeQuery = true)
    String findMaxDate();
}
