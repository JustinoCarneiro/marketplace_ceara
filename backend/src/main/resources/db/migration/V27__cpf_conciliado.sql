-- Duplicata legada ainda podia contratar a si mesma (achado da revisão cruzada, 2026-10-05): se um prestador legado e um
-- cliente já tinham o mesmo CPF, o backfill (ProviderCpfBackfill) deixava o prestador sem hash, mas o perfil VERIFICADO
-- continuava apto a propor — o self-hire checa só IDs de conta diferentes, nunca enxergando que é a MESMA pessoa por
-- trás das duas contas. cpf_conciliado=false marca o perfil cujo CPF colidiu com outra conta: o guard de verificação
-- passa a recusar operar (propor/aceitar) até o suporte resolver a duplicata à mão — sem suspender a conta sozinho.
ALTER TABLE providers_profile ADD COLUMN IF NOT EXISTS cpf_conciliado BOOLEAN NOT NULL DEFAULT TRUE;
