create table playerstatistics
(
    UUID             varchar(100) null,
    damageblocked    int          null,
    damagedealt      int          null,
    damagetaken      int          null,
    distanceclimbed  int          null,
    distancecrouched int          null,
    distancefallen   int          null,
    distancesprinted int          null,
    jumps            int          null,
    playerkills      int          null,
    sneaktime        int          null,
    targetshit       int          null,
    timeslept        int          null,
    constraint playerstatistics_statistic_update_UUID_fk
        foreign key (UUID) references inventory_update (UUID)
);

