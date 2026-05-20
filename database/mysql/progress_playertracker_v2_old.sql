create table progress_playertracker_v2_old
(
    id        bigint auto_increment
        primary key,
    datetime  datetime default CURRENT_TIMESTAMP not null,
    player    varchar(30) charset latin1         not null,
    timedelta int                                not null
);

