<!DOCTYPE html>
<html>
    <head>
        <title>{{ $title }}</title>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
        <meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
        <meta http-equiv="X-UA-Compatible" content="ie=edge">
        <style>
            .page-break {
                page-break-after: always;
            }
        </style>
    </head>
    <body style="width: 2480px !important; font-size: 16px; font-family: 'arabtype', Arial, sans-serif;">
        @php
            function numberToFrenchText($number) {
                $units = ['', 'un', 'deux', 'trois', 'quatre', 'cinq', 'six', 'sept', 'huit', 'neuf'];
                $teens = ['dix', 'onze', 'douze', 'treize', 'quatorze', 'quinze', 'seize', 'dix-sept', 'dix-huit', 'dix-neuf'];
                $tens = ['', '', 'vingt', 'trente', 'quarante', 'cinquante', 'soixante', 'soixante-dix', 'quatre-vingt', 'quatre-vingt-dix'];

                // Split the number into integer and decimal parts
                $integerPart = floor($number);
                $decimalPart = $number - $integerPart;

                // Convert integer part
                $integerText = convertIntegerPart($integerPart, $units, $teens, $tens);

                // Convert decimal part
                $decimalText = '';
                if ($decimalPart > 0) {
                    $decimalText = 'virgule ' . convertIntegerPart(round($decimalPart * 100), $units, $teens, $tens); // Convert decimal part as if it were the integer part
                }

                // Combine integer and decimal parts with appropriate connector
                return $integerText . ' ' . $decimalText;
            }

            function convertIntegerPart($number, $units, $teens, $tens) {
                if ($number == 0) {
                    return 'zéro';
                } elseif ($number < 10) {
                    return $units[$number];
                } elseif ($number < 20) {
                    return $teens[$number - 10];
                } elseif ($number < 100) {
                    $unit = $number % 10;
                    $ten = floor($number / 10);
                    if ($unit == 0) {
                        return $tens[$ten];
                    } elseif ($unit == 1 && ($ten == 7 || $ten == 9)) {
                        return $tens[$ten - 1] . '-et-un';
                    } elseif ($unit == 1) {
                        return $tens[$ten] . '-et-' . $units[$unit];
                    } elseif ($ten == 8) {
                        return $tens[$ten] . '-' . $units[$unit];
                    } elseif ($ten == 7 || $ten == 9) {
                        return $tens[$ten - 1] . '-' . $teens[$unit];
                    } else {
                        return $tens[$ten] . '-' . $units[$unit];
                    }
                } elseif ($number < 1000) {
                    $hundreds = floor($number / 100);
                    $remainder = $number % 100;
                    $text = ($hundreds > 1 ? $units[$hundreds] . ' ' : '') . 'cent';
                    if ($remainder > 0) {
                        $text .= ' ' . convertIntegerPart($remainder, $units, $teens, $tens);
                    }
                    return $text;
                } elseif ($number < 1000000) {
                    $thousands = floor($number / 1000);
                    $remainder = $number % 1000;
                    $text = ($thousands > 1 ? convertIntegerPart($thousands, $units, $teens, $tens) . ' ' : '') . 'mille';
                    if ($remainder > 0) {
                        $text .= ' ' . convertIntegerPart($remainder, $units, $teens, $tens);
                    }
                    return $text;
                } elseif ($number < 1000000000) {
                    $millions = floor($number / 1000000);
                    $remainder = $number % 1000000;
                    $text = convertIntegerPart($millions, $units, $teens, $tens) . ' million';
                    if ($remainder > 0) {
                        $text .= ' ' . convertIntegerPart($remainder, $units, $teens, $tens);
                    }
                    return $text;
                } else {
                    return 'Nombre hors de portée';
                }
            }
        @endphp
        @foreach($deplacements as $indexPersonne => $singlePersonne)
            <!-- ORDM -->
            @foreach($singlePersonne as $index => $singleDeplacement)
                @if($index > 0)
                    <div class="page-break"></div>
                @endif
                @if($show_order)
                    <div dir="rtl" style="font-size: 20px;line-height: 18px;">
                        <div style="font-weight: normal;">
                            <table style="width: 100%; font-weight: bold; border-collapse: collapse;">
                                <tr>
                                    <td style="text-align: right;">الرقم : {{ $singleDeplacement?->numero_ordm ? $singleDeplacement?->numero_ordm : null}}</td>
                                    <td style="text-align: left;">التاريخ : {!! $singleDeplacement?->date_ordm ? $singleDeplacement?->date_ordm?->format('d-m-Y') : '<span style="display: inline-block; width: 100%;">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>' !!}</td>
                                </tr>
                            </table>
                            <h2 style="text-align: center;">تـــكــلــيــف بــمــهــمــة</h2>
                            @if($singleDeplacement instanceof \App\Models\DeplacementPropre)
                                <div style="text-align: center;"><img src="{{ generateQrCode('DEPLACEMENTS>>>>>>>>>'.auth()->user()->id.'>>>>>>>>>'.$singleDeplacement?->id.'>>>>>>>>>'.date('Y-m-d').'>>>>>>>>>'.date('H:i:s'), 75, 5) }}" alt="QR Code"></div>
                            @else
                                <div style="text-align: center;"><img src="{{ generateQrCode('DEPLACEMENTS>>>>>>>>>PROGRAMMES>>>>>>>>>'.auth()->user()->id.'>>>>>>>>>'.$singleDeplacement?->id.'>>>>>>>>>'.date('Y-m-d').'>>>>>>>>>'.date('H:i:s'), 75, 5) }}" alt="QR Code"></div>
                            @endif
                        </div>
                        <div>
                            <h5>يتعين على السيد(ة)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singleDeplacement?->personne?->nom_prenom_ar }}</span></h5>
                            <h5>الرقــــم الترتيبـــــي&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singleDeplacement?->personne?->matricule }}</span></h5>
                            <h5>الإطـــــار&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singleDeplacement?->personne?->grade?->name_trans['ar'] }} {{ $singleDeplacement?->personne?->grade?->echelle <= 11 ? ('- السلم ' . $singleDeplacement?->personne?->grade?->echelle) : ($singleDeplacement?->personne?->grade?->echelle == 99 ? '- H.E' : null) }}</span></h5>
                            @if($singleDeplacement?->personne_id == 1461136)
                                <h5>المـهـمـة المـزاولـة&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ __('مدير التعاون الوطني') }}</span></h5>
                            @endif
                            @if($singleDeplacement?->personne_id == 1461016 && $singleDeplacement?->date_depart?->format('Y-m-d') >= '2025-10-20')
                                <h5>المـهـمـة المـزاولـة&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ __('نائب مدير التعاون الوطني') }}</span></h5>
                            @endif
                            <h5>بالـتـوجــــه إلـــــــى&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singleDeplacement?->arrivalDelegation?->name_trans['ar'] }}</span></h5>
                            <h5>مـوضـوع التنقــــل&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singleDeplacement?->objet }}</span></h5>
                            <h5>وسيلـــة النقـــــــل&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singleDeplacement?->moyenTransport?->code == 'voiture_de_service' || $singleDeplacement?->moyenTransport?->code == 'voiture_privee' ? $singleDeplacement?->moyenTransport?->name_trans['ar'] .' [ '. ($singleDeplacement?->matricule ? $singleDeplacement?->matricule : '_______________' ) .' ]' : $singleDeplacement?->moyenTransport?->name_trans['ar'] }}</span></h5>
                        </div>
                        <div>
                            <table style="width: 100%; border-collapse: collapse; margin: 10px 0px 0px 0px">
                                <thead>
                                    <tr>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;"></th>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">التاريخ</th>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">الساعة</th>
                                        @if($singleDeplacement?->personne?->affectation?->code == 'siege_centrale' && $singleDeplacement?->personne_id != 1461136)
                                            <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px; width: 25%;">توقيع الرئيس المباشر</th>
                                        @endif
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px; width: 30%;">توقيع المدير<br/>أو من ينوب عنه</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 30px 8px; font-size: 14px;">الذهاب</td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">{{ $singleDeplacement?->date_depart?->format('Y-m-d') }}</td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">{{ $singleDeplacement?->date_depart?->format('H:i') }}</td>
                                        @if($singleDeplacement?->personne?->affectation?->code == 'siege_centrale' && $singleDeplacement?->personne_id != 1461136)
                                            <td rowspan="8" style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;"></td>
                                        @endif
                                        <td rowspan="8" style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;"></td>
                                    </tr>
                                    <tr>
                                        <td style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 30px 8px; font-size: 14px;">الرجوع</td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">
                                            @if($singleDeplacement?->personne_id == 1461136 || ($singleDeplacement?->personne_id == 1461016 && $singleDeplacement?->date_depart?->format('Y-m-d') >= '2025-10-20'))
                                                {{ $singleDeplacement?->date_retour?->format('Y-m-d') }}
                                            @else
                                                {{ $singleDeplacement?->date_retour?->format('Y-m-d') }}
                                            @endif
                                        </td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">{{ $singleDeplacement?->date_retour?->format('H:i') }}</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                        @if($singleDeplacement instanceof \App\Models\DeplacementPropre)
                            @if($singleDeplacement?->personne?->structure?->code != 'inspection_generale')
                                <div style="text-align: center;">
                                    <h5>شهادة الحضور</h5>
                                </div>
                                <div>
                                    <table style="width: 100%; border-collapse: collapse; margin: 10px 0px 0px 0px">
                                        <thead>
                                            <tr>
                                                <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">القسم / المصلحة /<br/>المنسقية / المندوبية /<br/>المركز</th>
                                                <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">تاريخ الوصول</th>
                                                <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">تاريخ المغادرة</th>
                                                <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">توقيع وخاتم المسؤول</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 30px 8px; font-size: 14px;"><br/><br/><br/><br/></td>
                                                <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 30px 8px; font-size: 14px;"></td>
                                                <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 30px 8px; font-size: 14px;"></td>
                                                <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 30px 8px; font-size: 14px;"></td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            @endif
                        @endif
                    </div>
                @endif
            @endforeach
            <!-- ETAT -->
            @if($show_summary)
                <div class="page-break"></div>
                <!-- ETAT DEP -->
                <div dir="ltr">
                    <div style="line-height: 10px;">
                        <h4 style="width:100%; text-align: center;">ETAT RECAPITULATIF DES SOMMES DUES <br/> A</h4><br/><br/>
                        <p style="font-size: 14px; font-weight: bold;">Etat N°&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singlePersonne?->sortByDesc('numero_etat')?->first()?->numero_etat }}</span></p>
                        <p style="font-size: 14px; font-weight: bold;">Mr/Mme&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singlePersonne[0]?->personne?->nom_prenom_fr }}</span></p>
                        <p style="font-size: 14px; font-weight: bold;">Matricule&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singlePersonne[0]?->personne?->matricule }}</span></p>
                        <p style="font-size: 14px; font-weight: bold;">Grade&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singlePersonne[0]?->personne?->grade?->name_trans['fr'] }} {{ $singlePersonne[0]?->personne?->grade?->echelle <= 11 ? ('- échelle ' . $singlePersonne[0]?->personne?->grade?->echelle) : ($singlePersonne[0]?->personne?->grade?->echelle == 99 ? '- H.E' : null) }}</span></p>
                        @if($singlePersonne[0] instanceof \App\Models\DeplacementPropre && $singlePersonne[0]?->moyenTransport?->code == 'voiture_de_service')
                            <p style="font-size: 14px; font-weight: bold;">Voiture&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span dir="rtl" style="font-weight: normal;">{{ $singlePersonne[0]?->matricule }}</span></p>
                        @endif
                        <p style="font-size: 14px;">Pour les indemnités de déplacement suivantes :</p><br/>
                    </div>
                    <div style="line-height: 10px;">
                        <table style="width: 100%; border-collapse: collapse;">
                            <thead>
                                <tr>
                                    <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Ordre de Mission</th>
                                    <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Date</th>
                                    <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Lieux de déplacement</th>
                                    <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Heure départ</th>
                                    <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Heure retour</th>
                                    <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Nbr de repas</th>
                                    <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Taux indemnité</th>
                                    @if($singlePersonne[0]?->moyenTransport?->code == 'transports_en_commun' && $singlePersonne[0]?->frais_transport > 0)
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Frais de transport</th>
                                    @endif
                                    <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Décompte total</th>
                                </tr>
                            </thead>
                            <tbody>
                                @foreach($singlePersonne as $index => $singleDeplacement)
                                    <tr>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ 'N° ' . $singleDeplacement?->numero_ordm }}<br/><br/>{{ 'Du ' . $singleDeplacement?->date_ordm?->format('d-m-Y') }}</td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">
                                            {{ $singleDeplacement?->date_depart?->format('d-m-Y') }}
                                            <br/><br/>
                                            @if($singleDeplacement?->personne_id == 1461136 || ($singleDeplacement?->personne_id == 1461016 && $singleDeplacement?->date_depart?->format('Y-m-d') >= '2025-10-20'))
                                                {{ $singleDeplacement?->date_retour?->format('d-m-Y') }}
                                            @else
                                                {{ $singleDeplacement?->date_retour?->format('d-m-Y') }}
                                            @endif
                                        </td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ $singleDeplacement?->departDelegation?->name_trans['fr'] .' - '. $singleDeplacement?->arrivalDelegation?->name_trans['fr'] }}<br/><br/>{{ $singleDeplacement?->arrivalDelegation?->name_trans['fr'] .' - '. $singleDeplacement?->departDelegation?->name_trans['fr'] }}</td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ $singleDeplacement?->date_depart?->format('H:i') }}</td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ $singleDeplacement?->date_retour?->format('H:i') }}</td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">
                                            @if($singleDeplacement?->personne_id == 1461136 || ($singleDeplacement?->personne_id == 1461016 && $singleDeplacement?->date_depart?->format('Y-m-d') >= '2025-10-20'))
                                                {{ ($singleDeplacement?->nombre_repas_taux_1 / 3) }}@if($singleDeplacement?->nombre_repas_taux_2 > 0)<br/><br/>{{ ($singleDeplacement?->nombre_repas_taux_2 / 3) }}@endif
                                            @else
                                                {{ $singleDeplacement?->nombre_repas_taux_1 }}@if($singleDeplacement?->nombre_repas_taux_2 > 0)<br/><br/>{{ $singleDeplacement?->nombre_repas_taux_2 }}@endif
                                            @endif
                                        </td>
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">
                                            @if($singleDeplacement?->personne_id == 1461136 || ($singleDeplacement?->personne_id == 1461016 && $singleDeplacement?->date_depart?->format('Y-m-d') >= '2025-10-20'))
                                                {{ number_format(($singleDeplacement?->indemnite_taux_1 * 3), 2) .' DH' }}@if($singleDeplacement?->indemnite_taux_2 > 0 && $singleDeplacement?->nombre_repas_taux_2 > 0)<br/><br/>{{ number_format(($singleDeplacement?->indemnite_taux_2 * 3), 2) .' DH' }}@endif
                                            @else
                                                {{ number_format(($singleDeplacement?->indemnite_taux_1), 2) .' DH' }}@if($singleDeplacement?->indemnite_taux_2 > 0 && $singleDeplacement?->nombre_repas_taux_2 > 0)<br/><br/>{{ number_format($singleDeplacement?->indemnite_taux_2, 2) .' DH' }}@endif
                                            @endif
                                        </td>
                                        @if($singleDeplacement?->moyenTransport?->code == 'transports_en_commun' && $singleDeplacement?->frais_transport > 0)
                                            <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ number_format($singleDeplacement?->frais_transport, 2) . ' DH' }}</td>
                                        @endif
                                        <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ number_format((($singleDeplacement?->nombre_repas_taux_1 * $singleDeplacement?->indemnite_taux_1) + ($singleDeplacement?->nombre_repas_taux_2 * $singleDeplacement?->indemnite_taux_2) + ($singleDeplacement?->frais_transport > 0 ? $singleDeplacement?->frais_transport : 0)), 2) .' DH' }}</td>
                                    </tr>
                                @endforeach
                            </tbody>
                        </table>
                    </div>
                    <div style="font-size: 14px; text-align: right; margin: 10px 0px; font-weight: bold;">Total : {{ number_format(collect($singlePersonne)->sum(function($personneDeplacement){ return ($personneDeplacement->nombre_repas_taux_1 * $personneDeplacement->indemnite_taux_1) + ($personneDeplacement->nombre_repas_taux_2 * $personneDeplacement->indemnite_taux_2) + ($personneDeplacement->frais_transport > 0 ? $personneDeplacement->frais_transport : 0);}),2) }} DH</div>
                    <div style="font-size: 14px; margin: 10px 0px; font-weight: bold;">Arrêté le présent état à la somme de : <span>@php echo numberToFrenchText(collect($singlePersonne)->sum(function($personneDeplacement){ return ($personneDeplacement->nombre_repas_taux_1 * $personneDeplacement->indemnite_taux_1) + ($personneDeplacement->nombre_repas_taux_2 * $personneDeplacement->indemnite_taux_2) + ($personneDeplacement->frais_transport > 0 ? $personneDeplacement->frais_transport : 0);})) @endphp dirhams.</span></div>
                    @if($singlePersonne[0] instanceof \App\Models\DeplacementPropre)
                        <div style="text-align: center; line-height: 10px;"><img src="{{ generateQrCode('DEPLACEMENTS>>>>>>>>>'.auth()->user()->id.'>>>>>>>>>'.$singlePersonne[0]?->id.'>>>>>>>>>'.date('Y-m-d').'>>>>>>>>>'.date('H:i:s'), 75, 5) }}" alt="QR Code"></div>
                    @else
                        <div style="text-align: center; line-height: 10px;"><img src="{{ generateQrCode('DEPLACEMENTS>>>>>>>>>PROGRAMMES>>>>>>>>>'.auth()->user()->id.'>>>>>>>>>'.$singlePersonne[0]?->id.'>>>>>>>>>'.date('Y-m-d').'>>>>>>>>>'.date('H:i:s'), 75, 5) }}" alt="QR Code"></div>
                    @endif
                    <div style="margin: 10px 0px;">
                        <table style="width: 100%; border-collapse: collapse; text-align: center; font-size: 14px; font-weight: bold;">
                            <tr>
                                <td>
                                    <span>L'Ordonnateur</span>
                                    <br/>
                                    <span style="font-size: 10px;">(Sous Ordonnateurs)</span>
                                </td>
                                <td>
                                    <span>L'Intéressé</span>
                                </td>
                            </tr>
                        </table>
                    </div>
                </div>
                <!-- ETAT KM -->
                @if($singlePersonne[0] instanceof \App\Models\DeplacementPropre && $singlePersonne[0]?->moyenTransport?->code == 'voiture_privee' && $singlePersonne[0]?->taux_km > 0 && $singlePersonne[0]?->distance_trajet > 0 && $singlePersonne[0]?->nombre_trajet > 0)
                    <div class="page-break"></div>
                    <div dir="ltr">
                        <div style="text-align: center;">
                            <h3>ETAT RECAPITULATIF DES SOMMES DUES</h3>
                            <h3>INDEMNITES KILOMETRIQUES A</h3>
                        </div>
                        <div>
                            <p style="font-size: 14px; font-weight: bold;">Etat N°&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ 'K'.$singlePersonne[0]?->numero_etat }}</span></p>
                            <p style="font-size: 14px; font-weight: bold;">Mr/Mme&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singlePersonne[0]?->personne?->nom_prenom_fr }}</span></p>
                            <p style="font-size: 14px; font-weight: bold;">Matricule&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singlePersonne[0]?->personne?->matricule }}</span></p>
                            <p style="font-size: 14px; font-weight: bold;">Grade&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;<span style="font-weight: normal;">{{ $singlePersonne[0]?->personne?->grade?->name_trans['fr'] }} {{ $singlePersonne[0]?->personne?->grade?->echelle <= 11 ? ('- échelle ' . $singlePersonne[0]?->personne?->grade?->echelle) : ($singlePersonne[0]?->personne?->grade?->echelle == 99 ? '- H.E' : null) }}</span></p>
                            <p style="font-size: 14px;">Pour les indemnités kilométriques suivantes :</p><br/>
                        </div>
                        <div>
                            <table style="width: 100%; border-collapse: collapse;">
                                <thead>
                                    <tr>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Ordre de mission</th>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Date</th>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Lieux de déplacement</th>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Voiture</th>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Puiss. fisc.</th>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Distance kilométrique</th>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Taux indemnité kilométrique</th>
                                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 12px;">Décompte total</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    @foreach($singlePersonne as $index => $singleDeplacement)
                                        <tr>
                                            <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ 'N° ' . $singleDeplacement?->numero_ordm }}<br/><br/>{{ 'Du ' . $singleDeplacement?->date_ordm?->format('d-m-Y') }}</td>
                                            <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ $singleDeplacement?->date_depart?->format('d-m-Y') }}<br/><br/>{{ $singleDeplacement?->date_retour?->format('d-m-Y') }}</td>
                                            <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ $singleDeplacement?->departDelegation?->name_trans['fr'] .' - '. $singleDeplacement?->arrivalDelegation?->name_trans['fr'] }}<br/><br/>{{ $singleDeplacement?->arrivalDelegation?->name_trans['fr'] .' - '. $singleDeplacement?->departDelegation?->name_trans['fr'] }}</td>
                                            <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ $singleDeplacement?->nom_voiture_fr }}<br/><br/><span dir="rtl" style="display: inline-block; white-space: nowrap;">{{ $singleDeplacement?->matricule }}</span></td>
                                            <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ $singleDeplacement?->puissanceFiscale?->name_trans['fr'] }}</td>
                                            <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ $singleDeplacement?->distance_trajet }} * {{ $singleDeplacement?->nombre_trajet }}</td>
                                            <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ $singleDeplacement?->taux_km . ' DH' }}</td>
                                            <td style="border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 11px;">{{ (($singleDeplacement?->distance_trajet * $singleDeplacement?->nombre_trajet) * $singleDeplacement?->taux_km) . ' DH' }}</td>
                                        </tr>
                                    @endforeach
                                </tbody>
                            </table>
                        </div>
                        <div style="font-size: 14px; text-align: right; margin: 20px 0px; font-weight: bold;">Total : {{ (($singlePersonne[0]?->distance_trajet * $singlePersonne[0]?->nombre_trajet) * $singlePersonne[0]?->taux_km) . ' DH' }}</div>
                        <div style="font-size: 14px; margin: 20px 0px 10px 0px; font-weight: bold;">Arrêté le présent état à la somme de : <span>@php echo numberToFrenchText((($singlePersonne[0]?->distance_trajet * $singlePersonne[0]?->nombre_trajet) * $singlePersonne[0]?->taux_km)) @endphp dirhams.</span></div>
                        <div style="margin: 10px 0px;">
                            <table style="width: 100%; border-collapse: collapse; text-align: center; font-size: 14px; font-weight: bold;">
                                <tr>
                                    <td>
                                        <span>L'Ordonnateur</span>
                                        <br/>
                                        <span style="font-size: 10px;">(Sous Ordonnateurs)</span>
                                    </td>
                                    <td>
                                        @if($singlePersonne[0] instanceof \App\Models\DeplacementPropre)
                                            <span style="text-align: center;"><img src="{{ generateQrCode('DEPLACEMENTS>>>>>>>>>'.auth()->user()->id.'>>>>>>>>>'.$singlePersonne[0]?->id.'>>>>>>>>>'.date('Y-m-d').'>>>>>>>>>'.date('H:i:s'), 75, 5) }}" alt="QR Code"></span>
                                        @else
                                            <span style="text-align: center;"><img src="{{ generateQrCode('DEPLACEMENTS>>>>>>>>>PROGRAMMES>>>>>>>>>'.auth()->user()->id.'>>>>>>>>>'.$singlePersonne[0]?->id.'>>>>>>>>>'.date('Y-m-d').'>>>>>>>>>'.date('H:i:s'), 75, 5) }}" alt="QR Code"></span>
                                        @endif
                                    </td>
                                    <td>
                                        <span>L'Intéressé</span>
                                    </td>
                                </tr>
                            </table>
                        </div>
                    </div>
                @endif
                <div class="page-break"></div>
            @endif
            <!-- ChunkSeparator -->
        @endforeach
    </body>
</html>
