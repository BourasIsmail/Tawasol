<!DOCTYPE html>
<html>
    <head>
        <title>{{ $title }}</title>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
        <meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
        <meta http-equiv="X-UA-Compatible" content="ie=edge">
        <style>
            .page-break {
                page-break-after: always;
            }
        </style>
    </head>
    <body>
        <div>
            <div style="font-size: 14px; text-align: center; margin: 20px 0px; font-weight: bold;">ETAT GLOBAL DES SOMMES DUES</div>
            <table dir="ltr" style="width: 100%; border-collapse: collapse; margin: 10px 0px 0px 0px;">
                <thead>
                    <tr>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">N°</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">N° ETAT</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">ANNEE</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">MATRICULE</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">NOM ET PRENOM</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">GRADE</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">MONTANT</th>
                    </tr>
                </thead>
                @php
                    $sommePropre = 0;
                    $sommeCOAPH = 0;
                    $sommeEMF = 0;
                    $sommeCFA = 0;
                    $sommeOther = 0;
                    $sommeTotal = 0;
                @endphp
                <tbody>
                    @foreach($personnes as $index => $personne)
                        <tr>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px; text-transform: uppercase;">{{ ($index + 1) }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px; text-transform: uppercase;">{{ $personne?->deplacementsProgramme?->sortByDesc('numero_etat')?->first()?->numero_etat ?? '-' }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px; text-transform: uppercase;">{{ $personne?->deplacementsProgramme?->sortByDesc('numero_etat')?->first()?->annee_id ?? '-' }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px; text-transform: uppercase;">{{ $personne?->matricule ?? '-' }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px; text-transform: uppercase;">{{ $personne?->nom_prenom_fr ?? '-' }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px; text-transform: uppercase;">{{ ($personne?->grade?->name_trans['fr'] || $personne?->grade?->echelle) ? ($personne?->grade?->name_trans['fr'] .' - '. ($personne?->grade?->echelle <= 11 ? $personne?->grade?->echelle : ($personne?->grade?->echelle == 99 ? 'H.E' : ''))) : '-' }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px; text-transform: uppercase;">
                                @php
                                    $somme = 0;
                                @endphp
                                @foreach($personne?->deplacementsProgramme as $deplacement)
                                    @php
                                        $somme += ($deplacement->nombre_repas_taux_1 * $deplacement->indemnite_taux_1) + ($deplacement->nombre_repas_taux_2 * $deplacement->indemnite_taux_2);
                                    @endphp
                                @endforeach
                                @php
                                    $numEtats = collect(
                                        explode('/', (string) $personne?->deplacementsProgramme
                                            ?->sortByDesc('numero_etat')
                                            ?->first()?->numero_etat)
                                    )->map(fn ($v) => trim($v))->filter()->values();
                                @endphp
                                @if($numEtats[0] ?? null)
                                    @if(in_array($numEtats->first(), ['EN-P',  'EN-SDI', 'EN-CDC', 'EN-CSD', 'EN-ANNEXE-3', 'EN-ANNEXE-1', 'EN-FP', 'EN-PTP', 'EN-AAC', 'EN-CI-1', 'EN-CI-2', 'EN-AACS', 'EN-ANNEXE-2', 'EN-ANNEXE-4', 'EN-ANNEXE-5', 'EN-ANNEXE-6', 'EN-DIR', 'EN-ANNEXE-7', 'EN2-CDC', 'EN-PANX', 'EN-PG', 'EN-P70', 'EN-P72', 'EN-P80', 'EN-P88', 'EN-P90', 'EN-P96', 'EN-P100', 'EN-P150', 'EN-P200', 'EN-P300', 'EN-P400', 'EN-P500', 'EN-P800', 'EN-PR30', 'EN-PR50', 'EN-PR100', 'EN-PR180', 'EN-PR200', 'EN-PR400'], true))
                                        @php $sommePropre += $somme; @endphp
                                    @elseif(in_array($numEtats->first(), ['COAPH', 'COAPH-PANX', 'COAPH-PG', 'EN-COAPH30', 'EN-COAPH50', 'EN-COAPH100', 'EN-COAPH180', 'EN-COAPH200'], true))
                                        @php $sommeCOAPH += $somme; @endphp
                                    @elseif(in_array($numEtats->first(), ['EMF', 'EMF-PANX', 'EMF-PG', 'EN-EMF30', 'EN-EMF50', 'EN-EMF100', 'EN-EMF180', 'EN-EMF200'], true))
                                        @php $sommeEMF += $somme; @endphp
                                    @elseif(in_array($numEtats->first(), ['CFA', 'CFA-PANX', 'CFA-PG', 'EN-CFA30', 'EN-CFA50', 'EN-CFA100', 'EN-CFA180', 'EN-CFA200'], true))
                                        @php $sommeCFA += $somme; @endphp
                                    @else
                                        @php $sommeOther += $somme; @endphp
                                    @endif
                                @endif
                                @php $sommeTotal += $somme; @endphp
                                {{ number_format($somme ?? 0, 2, ',', ' ') }}
                            </td>
                        </tr>
                    @endforeach
                    <tr>
                        <td colspan="6" style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Budget : PROPRE</td>
                        <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">{{ number_format($sommePropre ?? 0, 2, ',', ' ') }}</td>
                    </tr>
                    <tr>
                        <td colspan="6" style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Budget : COAPH</td>
                        <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">{{ number_format($sommeCOAPH ?? 0, 2, ',', ' ') }}</td>
                    </tr>
                    <tr>
                        <td colspan="6" style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Budget : EMF</td>
                        <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">{{ number_format($sommeEMF ?? 0, 2, ',', ' ') }}</td>
                    </tr>
                    <tr>
                        <td colspan="6" style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Budget : CFA</td>
                        <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">{{ number_format($sommeCFA ?? 0, 2, ',', ' ') }}</td>
                    </tr>
                    <tr>
                        <td colspan="6" style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Autres Budgets</td>
                        <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">{{ number_format($sommeOther ?? 0, 2, ',', ' ') }}</td>
                    </tr>
                </tbody>
                <tfoot>
                    <tr>
                        <td colspan="6" style="background-color: #d9d9d9; border: 1px solid #c6c6c6; text-align: center; padding: 8px; font-size: 14px; font-weight: bold;">Total</td>
                        <td style="background-color: #d9d9d9; border: 1px solid #c6c6c6; text-align: center; padding: 8px; font-size: 14px; font-weight: bold;">{{ number_format($sommeTotal ?? 0, 2, ',', ' ') }}</td>
                    </tr>
                </tfoot>
            </table>
            <div style="font-size: 14px; text-align: center; margin: 20px 0px; font-weight: bold;">Signature:</div>
        </div>
    </body>
</html>
