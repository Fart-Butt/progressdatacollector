create table playerstatistics
(
    UUID             varchar(100) null,
    damageblocked    int          null,
    damagedealt      int          null,
    damagetaken      int          null comment '/10',
    distanceclimbed  int          null,
    distancecrouched int          null,
    distancefallen   int          null,
    distancesprinted int          null,
    distancewalked   int          null comment '/100',
    jumps            int          null,
    playerkills      int          null,
    targetshit       int          null,
    timeslept        int          null,
    constraint playerstatistics_statistic_update_UUID_fk
        foreign key (UUID) references inventory_update (UUID)
)
    comment '/10 for hp damage';

