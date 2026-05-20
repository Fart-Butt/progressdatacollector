create table progres_cheevos
(
    player_name varchar(100)                       null,
    datetime    datetime default CURRENT_TIMESTAMP null,
    cheevo_text varchar(150)                       null,
    constraint progres_cheevos_minecraft_players_player_name_fk
        foreign key (player_name) references minecraft_players (player_name)
);

