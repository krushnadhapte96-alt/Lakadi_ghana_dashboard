package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Return;
import com.ghana.dashboard.domain.ReturnPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReturnPhotoRepository extends JpaRepository<ReturnPhoto, Long> {

    List<ReturnPhoto> findByReturnRequestOrderByIdAsc(Return returnRequest);

    List<ReturnPhoto> findByReturnRequestIn(Collection<Return> returns);
}
