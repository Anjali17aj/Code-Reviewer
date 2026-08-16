package com.codereview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "codebase_group_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodebaseGroupFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long codebaseGroupId;

    @Column(nullable = false)
    private Long fileId;
}
