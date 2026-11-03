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
            <table style="width: 100%; border-collapse: collapse; margin: 10px 0px 0px 0px">
                <thead>
                    <tr>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">الرقم الترتيبي</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">الاسم</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">الدرجة</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">التعيين</th>
                        <th style="background-color: #f2f2f2; border: 1px solid #dddddd; text-align: center; padding: 8px; font-size: 14px;">لائحة {{ $typeName }}</th>
                    </tr>
                </thead>
                <tbody>
                    @foreach($personnes as $personne)
                        <tr>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px;">{{ $personne?->matricule ? $personne?->matricule : '-' }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px;">{{ $personne?->nom_prenom_ar ? $personne?->nom_prenom_ar : '-' }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px;">{{ $personne?->grade?->name_trans['ar'] ? $personne?->grade?->name_trans['ar'] : '-' }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px;">{{ $personne?->affectation?->name_trans['ar'] ? $personne?->affectation?->name_trans['ar'] : '-' }}</td>
                            <td style="background-color: #ffffff; border: 1px solid #dddddd; text-align: center; padding: 5px 8px; font-size: 12px;">
                                <ul>
                                    @php
                                        $currrentYear = date('Y');
                                        $lastYear = $currrentYear - 1;
                                        if($type == 'propre'){
                                            $deplacements = $personne?->deplacements;
                                        }elseif($type == 'programme'){
                                            $deplacements = $personne?->deplacementsProgramme;
                                        }else{
                                            $deplacements = null;
                                        }
                                    @endphp
                                    @foreach($deplacements as $deplacement)
                                        @if(($deplacement?->status?->code == 'finalized' || $deplacement?->status?->code == 'paid') && ($deplacement?->annee?->annee == $currrentYear || $deplacement?->annee?->annee == $lastYear))
                                            <li>من {{ $deplacement?->date_depart?->format('d-m-Y H:i') }} إلى {{ $deplacement?->date_retour?->format('d-m-Y H:i') }} | {{ $deplacement?->objet }}</li>
                                        @endif
                                    @endforeach
                                </ul>
                            </td>
                        </tr>
                    @endforeach
                </tbody>
            </table>
        </div>
    </body>
</html>
