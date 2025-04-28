package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.OpdsUser;
import com.adityachandel.booklore.model.dto.Shelf;
import com.adityachandel.booklore.model.entity.OpdsUserEntity;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OpdsUserMapper {

    OpdsUser toOpdsUser(OpdsUserEntity opdsUserEntity);
}
