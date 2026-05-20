create table progress_NSA_POI
(
    id                 int auto_increment
        primary key,
    datetime           datetime     default CURRENT_TIMESTAMP not null,
    player             varchar(50)                            not null,
    dimension          varchar(100) default 'World'           not null,
    poi_estimated_size int          default 100               not null,
    x                  int                                    not null,
    y                  int          default 63                not null,
    z                  int                                    not null,
    verified           tinyint(1)   default 0                 not null,
    constraint progress_NSA_POI_UN
        unique (player)
);

